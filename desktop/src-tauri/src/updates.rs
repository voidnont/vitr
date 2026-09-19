use std::{
    path::{Path, PathBuf},
    process::Command,
    time::Duration,
};

use futures_util::StreamExt;
use serde_json::Value;
use sha2::{Digest, Sha256};
use tauri::AppHandle;
use tokio::{fs::File, io::AsyncWriteExt};

use crate::models::ClientUpdateStatus;

const RELEASES_API: &str = "https://api.github.com/repos/bloodvitr/vitr/releases/latest";
const SOURCE_RELEASES_API: &str = "https://api.github.com/repos/voidnont/vitr/releases/latest";
const RELEASES_PAGE: &str = "https://github.com/bloodvitr/vitr/releases";
const SOURCE_RELEASES_PAGE: &str = "https://github.com/voidnont/vitr/releases";

fn version_parts(value: &str) -> Vec<u64> {
    value
        .trim()
        .trim_start_matches('v')
        .split(|c| c == '.' || c == '-' || c == '+')
        .take(3)
        .map(|part| part.parse::<u64>().unwrap_or(0))
        .chain(std::iter::repeat(0))
        .take(3)
        .collect()
}

fn is_newer(latest: &str, current: &str) -> bool {
    version_parts(latest) > version_parts(current)
}

fn github_client() -> Result<reqwest::Client, String> {
    reqwest::Client::builder()
        .user_agent(concat!("vitr/", env!("CARGO_PKG_VERSION")))
        .build()
        .map_err(|e| format!("Could not initialize Vitr update client: {e}"))
}

async fn fetch_release(api: &str) -> Result<Option<Value>, String> {
    let response = github_client()?
        .get(api)
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .send()
        .await
        .map_err(|e| format!("Could not check Vitr releases: {e}"))?;

    if response.status().as_u16() == 404 {
        return Ok(None);
    }

    let response = response
        .error_for_status()
        .map_err(|e| format!("Vitr releases returned an error: {e}"))?;

    response
        .json()
        .await
        .map(Some)
        .map_err(|e| format!("Could not read Vitr release metadata: {e}"))
}

async fn latest_release() -> Result<Option<Value>, String> {
    if let Some(release) = fetch_release(RELEASES_API).await? {
        return Ok(Some(release));
    }
    fetch_release(SOURCE_RELEASES_API).await
}

fn release_version(value: &Value) -> String {
    value
        .get("tag_name")
        .and_then(Value::as_str)
        .unwrap_or("")
        .trim_start_matches('v')
        .to_string()
}

fn expected_asset_name(version: &str) -> Result<String, String> {
    #[cfg(all(target_os = "windows", target_arch = "x86_64"))]
    {
        return Ok(format!("Vitr-{version}-windows-x64.exe"));
    }
    #[cfg(all(target_os = "linux", target_arch = "x86_64"))]
    {
        return Ok(format!("Vitr-{version}-linux-x64.AppImage"));
    }
    #[cfg(target_os = "macos")]
    {
        return Ok(format!("Vitr-{version}-macos.dmg"));
    }
    #[allow(unreachable_code)]
    Err("Automatic Vitr installation is not packaged for this platform/architecture yet.".to_string())
}

async fn download_release_asset(release: &Value, version: &str) -> Result<PathBuf, String> {
    let expected_name = expected_asset_name(version)?;
    let asset = release
        .get("assets")
        .and_then(Value::as_array)
        .and_then(|assets| {
            assets.iter().find(|asset| {
                asset.get("name").and_then(Value::as_str) == Some(expected_name.as_str())
            })
        })
        .ok_or_else(|| format!("Release {version} does not contain {expected_name}."))?;

    let download_url = asset
        .get("browser_download_url")
        .and_then(Value::as_str)
        .ok_or_else(|| "Vitr release asset has no download URL.".to_string())?;
    if !download_url.starts_with("https://github.com/bloodvitr/vitr/")
        && !download_url.starts_with("https://github.com/voidnont/vitr/")
    {
        return Err("Refusing an update asset outside the Vitr GitHub release channels.".to_string());
    }

    let expected_digest = asset
        .get("digest")
        .and_then(Value::as_str)
        .and_then(|value| value.strip_prefix("sha256:"))
        .map(str::to_ascii_lowercase);

    let response = github_client()?
        .get(download_url)
        .send()
        .await
        .map_err(|e| format!("Could not download Vitr {version}: {e}"))?
        .error_for_status()
        .map_err(|e| format!("Vitr update download failed: {e}"))?;

    let path = std::env::temp_dir().join(&expected_name);
    let mut file = File::create(&path)
        .await
        .map_err(|e| format!("Could not create temporary Vitr installer: {e}"))?;
    let mut hasher = Sha256::new();
    let mut stream = response.bytes_stream();

    while let Some(chunk) = stream.next().await {
        let chunk = chunk.map_err(|e| format!("Could not read Vitr update download: {e}"))?;
        hasher.update(&chunk);
        file.write_all(&chunk)
            .await
            .map_err(|e| format!("Could not write Vitr update download: {e}"))?;
    }
    file.flush()
        .await
        .map_err(|e| format!("Could not finish Vitr update download: {e}"))?;

    if let Some(expected) = expected_digest {
        let actual = format!("{:x}", hasher.finalize());
        if actual != expected {
            let _ = tokio::fs::remove_file(&path).await;
            return Err("Downloaded Vitr update failed the GitHub SHA-256 integrity check.".to_string());
        }
    }

    Ok(path)
}

#[cfg(target_os = "windows")]
fn launch_update(app: &AppHandle, path: &Path) -> Result<String, String> {
    Command::new(path)
        .arg("/S")
        .spawn()
        .map_err(|e| format!("Could not launch the Vitr Windows installer: {e}"))?;

    let handle = app.clone();
    std::thread::spawn(move || {
        std::thread::sleep(Duration::from_millis(900));
        handle.exit(0);
    });
    Ok("Vitr update installer started. The app will close and reopen after installation.".to_string())
}

#[cfg(target_os = "linux")]
fn launch_update(app: &AppHandle, path: &Path) -> Result<String, String> {
    use std::os::unix::fs::PermissionsExt;

    if let Ok(current_appimage) = std::env::var("APPIMAGE") {
        let current = PathBuf::from(current_appimage);
        if current.is_file() {
            let backup = current.with_extension("AppImage.vitr-old");
            let _ = std::fs::remove_file(&backup);
            std::fs::rename(&current, &backup)
                .map_err(|e| format!("Could not prepare the current Vitr AppImage for update: {e}"))?;
            if let Err(error) = std::fs::copy(path, &current) {
                let _ = std::fs::rename(&backup, &current);
                return Err(format!("Could not install the new Vitr AppImage: {error}"));
            }
            let mut permissions = std::fs::metadata(&current)
                .map_err(|e| format!("Could not read updated AppImage permissions: {e}"))?
                .permissions();
            permissions.set_mode(0o755);
            std::fs::set_permissions(&current, permissions)
                .map_err(|e| format!("Could not make the updated AppImage executable: {e}"))?;
            Command::new(&current)
                .spawn()
                .map_err(|e| format!("Could not restart updated Vitr: {e}"))?;
            let _ = std::fs::remove_file(backup);
            app.exit(0);
            return Ok("Vitr AppImage updated and restarted.".to_string());
        }
    }

    Command::new("xdg-open")
        .arg(path)
        .spawn()
        .map_err(|e| format!("Could not open the downloaded Vitr AppImage: {e}"))?;
    Ok("Downloaded the new Vitr AppImage. Open it to finish updating.".to_string())
}

#[cfg(target_os = "macos")]
fn launch_update(_app: &AppHandle, path: &Path) -> Result<String, String> {
    Command::new("open")
        .arg(path)
        .spawn()
        .map_err(|e| format!("Could not open the Vitr macOS update: {e}"))?;
    Ok("Vitr update DMG opened. Replace Vitr in Applications to finish updating.".to_string())
}

#[cfg(not(any(target_os = "windows", target_os = "linux", target_os = "macos")))]
fn launch_update(_app: &AppHandle, _path: &Path) -> Result<String, String> {
    Err("Automatic desktop updates are not supported on this platform.".to_string())
}

#[tauri::command]
pub async fn check_client_update() -> Result<ClientUpdateStatus, String> {
    let current = env!("CARGO_PKG_VERSION").to_string();
    let Some(value) = latest_release().await? else {
        return Ok(ClientUpdateStatus {
            current_version: current.clone(),
            latest_version: current,
            update_available: false,
            release_url: RELEASES_PAGE.to_string(),
            notes: "No published Vitr release is available yet.".to_string(),
        });
    };

    let latest = release_version(&value);
    let release_url = value
        .get("html_url")
        .and_then(Value::as_str)
        .unwrap_or(RELEASES_PAGE)
        .to_string();
    let notes = value
        .get("body")
        .and_then(Value::as_str)
        .unwrap_or("")
        .to_string();

    Ok(ClientUpdateStatus {
        current_version: current.clone(),
        latest_version: if latest.is_empty() { current.clone() } else { latest.clone() },
        update_available: !latest.is_empty() && is_newer(&latest, &current),
        release_url,
        notes,
    })
}

#[tauri::command]
pub async fn install_client_update(app: AppHandle) -> Result<String, String> {
    let current = env!("CARGO_PKG_VERSION").to_string();
    let release = latest_release()
        .await?
        .ok_or_else(|| "No published Vitr release is available yet.".to_string())?;
    let latest = release_version(&release);
    if latest.is_empty() || !is_newer(&latest, &current) {
        return Ok(format!("Vitr {current} is already up to date."));
    }

    let installer = download_release_asset(&release, &latest).await?;
    launch_update(&app, &installer)
}

#[tauri::command]
pub fn open_release_page(url: Option<String>) -> Result<(), String> {
    let url = url
        .filter(|value| value.starts_with("https://github.com/bloodvitr/vitr/") || value.starts_with("https://github.com/voidnont/vitr/"))
        .unwrap_or_else(|| RELEASES_PAGE.to_string());

    #[cfg(target_os = "windows")]
    {
        Command::new("cmd")
            .args(["/C", "start", "", &url])
            .spawn()
            .map_err(|e| format!("Could not open Vitr releases: {e}"))?;
    }
    #[cfg(target_os = "macos")]
    {
        Command::new("open")
            .arg(&url)
            .spawn()
            .map_err(|e| format!("Could not open Vitr releases: {e}"))?;
    }
    #[cfg(all(unix, not(target_os = "macos")))]
    {
        Command::new("xdg-open")
            .arg(&url)
            .spawn()
            .map_err(|e| format!("Could not open Vitr releases: {e}"))?;
    }

    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn compares_versions() {
        assert!(is_newer("0.3.1", "0.3.0"));
        assert!(!is_newer("0.3.1", "0.3.1"));
        assert!(!is_newer("0.3.0", "0.3.1"));
    }

    #[test]
    fn official_update_assets_are_expected() {
        let asset = expected_asset_name("0.3.1");
        #[cfg(any(
            all(target_os = "windows", target_arch = "x86_64"),
            all(target_os = "linux", target_arch = "x86_64"),
            target_os = "macos"
        ))]
        assert!(asset.unwrap().starts_with("Vitr-0.3.1-"));
    }
}
