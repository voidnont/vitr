mod downloads;
mod lyrics;
mod media_controls;
mod models;
mod runtime;
mod search;
mod tray;

use std::sync::Mutex;
use tauri::{Emitter, LogicalPosition, LogicalSize, Manager, WebviewUrl, WebviewWindowBuilder};
use tauri::webview::{Color, PageLoadEvent};
use tauri_plugin_updater::UpdaterExt;

#[derive(Default)]
struct MiniPlayerState(Mutex<Option<serde_json::Value>>);

#[tauri::command]
fn start_drag_window(window: tauri::WebviewWindow) -> Result<(), String> {
    window
        .start_dragging()
        .map_err(|error| format!("Could not drag vitr window: {error}"))
}

#[tauri::command]
fn minimize_window(window: tauri::WebviewWindow) -> Result<(), String> {
    window
        .minimize()
        .map_err(|error| format!("Could not minimize vitr: {error}"))
}

#[tauri::command]
fn toggle_maximize_window(window: tauri::WebviewWindow) -> Result<(), String> {
    let maximized = window
        .is_maximized()
        .map_err(|error| format!("Could not read vitr window state: {error}"))?;
    if maximized {
        window
            .unmaximize()
            .map_err(|error| format!("Could not restore vitr window: {error}"))
    } else {
        window
            .maximize()
            .map_err(|error| format!("Could not maximize vitr: {error}"))
    }
}

fn mini_player_dimensions(layout: &str) -> (f64, f64) {
    if layout == "square" {
        (210.0, 210.0)
    } else {
        (340.0, 88.0)
    }
}

fn mini_player_position(app: &tauri::AppHandle, width: f64, height: f64) -> Option<(f64, f64)> {
    const EDGE: f64 = 20.0;
    const TASKBAR_GAP: f64 = 52.0;
    let monitor = app
        .get_webview_window("main")
        .and_then(|window| window.current_monitor().ok().flatten())
        .or_else(|| app.primary_monitor().ok().flatten())?;

    let scale = monitor.scale_factor();
    let size = monitor.size();
    let position = monitor.position();
    let left = position.x as f64 / scale;
    let top = position.y as f64 / scale;
    let monitor_width = size.width as f64 / scale;
    let monitor_height = size.height as f64 / scale;
    Some((
        left + monitor_width - width - EDGE,
        top + monitor_height - height - TASKBAR_GAP,
    ))
}

fn apply_mini_player_layout(
    app: &tauri::AppHandle,
    window: &tauri::WebviewWindow,
    layout: &str,
) -> Result<(), String> {
    let (width, height) = mini_player_dimensions(layout);
    window
        .set_size(LogicalSize::new(width, height))
        .map_err(|error| format!("Could not resize vitr mini player: {error}"))?;
    if let Some((x, y)) = mini_player_position(app, width, height) {
        window
            .set_position(LogicalPosition::new(x, y))
            .map_err(|error| format!("Could not position vitr mini player: {error}"))?;
    }
    Ok(())
}

#[tauri::command]
async fn set_mini_player_enabled(
    app: tauri::AppHandle,
    enabled: bool,
    layout: String,
) -> Result<(), String> {
    if let Some(window) = app.get_webview_window("mini") {
        if enabled {
            apply_mini_player_layout(&app, &window, &layout)?;
            return window
                .show()
                .map_err(|error| format!("Could not show vitr mini player: {error}"));
        }
        return window
            .hide()
            .map_err(|error| format!("Could not hide vitr mini player: {error}"));
    }

    if !enabled {
        return Ok(());
    }

    let (width, height) = mini_player_dimensions(&layout);
    let mut builder = WebviewWindowBuilder::new(&app, "mini", WebviewUrl::App("mini.html".into()))
        .title("vitr mini player")
        .inner_size(width, height)
        .resizable(false)
        .decorations(false)
        .always_on_top(true)
        .skip_taskbar(true)
        .shadow(true)
        .visible(false)
        .background_color(Color(9, 10, 15, 255))
        .on_page_load(|window, payload| {
            if matches!(payload.event(), PageLoadEvent::Finished) {
                let _ = window.show();
            }
        });

    if let Some((x, y)) = mini_player_position(&app, width, height) {
        builder = builder.position(x, y);
    }

    builder
        .build()
        .map_err(|error| format!("Could not create vitr mini player: {error}"))?;

    Ok(())
}

#[tauri::command]
fn set_mini_player_layout(app: tauri::AppHandle, layout: String) -> Result<(), String> {
    if let Some(window) = app.get_webview_window("mini") {
        apply_mini_player_layout(&app, &window, &layout)?;
    }
    Ok(())
}

#[tauri::command]
fn update_mini_player(
    app: tauri::AppHandle,
    state: tauri::State<'_, MiniPlayerState>,
    snapshot: serde_json::Value,
) -> Result<(), String> {
    {
        let mut current = state
            .0
            .lock()
            .map_err(|_| "Could not lock vitr mini player state".to_string())?;
        *current = Some(snapshot.clone());
    }
    if let Some(window) = app.get_webview_window("mini") {
        window
            .emit("mini-player-state", snapshot)
            .map_err(|error| format!("Could not update vitr mini player: {error}"))?;
    }
    Ok(())
}

#[tauri::command]
fn mini_player_state(state: tauri::State<'_, MiniPlayerState>) -> Result<Option<serde_json::Value>, String> {
    state
        .0
        .lock()
        .map(|current| current.clone())
        .map_err(|_| "Could not read vitr mini player state".to_string())
}

#[tauri::command]
fn mini_player_action(app: tauri::AppHandle, action: String) -> Result<(), String> {
    if action == "disable" {
        if let Some(window) = app.get_webview_window("mini") {
            window
                .hide()
                .map_err(|error| format!("Could not hide vitr mini player: {error}"))?;
        }
        if let Some(main) = app.get_webview_window("main") {
            main.emit("mini-player-disabled", ())
                .map_err(|error| format!("Could not notify vitr: {error}"))?;
        }
        return Ok(());
    }

    if !matches!(action.as_str(), "toggle-play" | "next" | "previous") {
        return Err("Unsupported mini player action".to_string());
    }

    if let Some(main) = app.get_webview_window("main") {
        main.emit("mini-player-command", serde_json::json!({ "action": action }))
            .map_err(|error| format!("Could not control vitr playback: {error}"))?;
    }
    Ok(())
}

#[tauri::command]
async fn auto_update(app: tauri::AppHandle) -> Result<Option<String>, String> {
    let updater = app.updater().map_err(|error| format!("Updater unavailable: {error}"))?;
    let Some(update) = updater
        .check()
        .await
        .map_err(|error| format!("Update check failed: {error}"))?
    else {
        return Ok(None);
    };

    let version = update.version.clone();
    update
        .download_and_install(|_, _| {}, || {})
        .await
        .map_err(|error| format!("Update install failed: {error}"))?;

    #[cfg(not(target_os = "windows"))]
    app.restart();

    Ok(Some(version))
}

#[tauri::command]
fn exit_app(app: tauri::AppHandle) {
    app.exit(0);
}

pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .plugin(tauri_plugin_updater::Builder::new().build())
        .manage(downloads::DownloadManager::default())
        .manage(MiniPlayerState::default())
        .setup(|app| {
            if let Err(error) = tray::install(app) {
                eprintln!("vitr system tray unavailable: {error}");
            }
            if let Err(error) = media_controls::install(app) {
                eprintln!("vitr native media controls unavailable: {error}");
            }
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            search::innertube_search,
            search::innertube_catalog_search,
            search::ytdlp_search,
            search::resolve_stream_url,
            downloads::authorize_media_path,
            downloads::scan_downloads,
            downloads::clear_removed_downloads,
            downloads::download_already_exists,
            downloads::download_track,
            downloads::cancel_download,
            downloads::remove_download,
            lyrics::fetch_metadata_lyrics,
            runtime::update_runtime_dependencies,
            runtime::current_runtime_status,
            tray::set_tray_enabled,
            media_controls::update_media_controls,
            start_drag_window,
            minimize_window,
            toggle_maximize_window,
            set_mini_player_enabled,
            set_mini_player_layout,
            update_mini_player,
            mini_player_state,
            mini_player_action,
            auto_update,
            exit_app
        ])
        .run(tauri::generate_context!())
        .expect("vitr failed to start");
}
