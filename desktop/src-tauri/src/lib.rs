use tauri::{AppHandle, LogicalPosition, LogicalSize, Manager, WebviewWindow};

fn mini_dimensions(mode: &str) -> (f64, f64, bool) {
    match mode {
        "lyrics" => (250.0, 250.0, true),
        "tiny" => (230.0, 54.0, false),
        "ultra" => (160.0, 40.0, false),
        _ => (348.0, 108.0, false),
    }
}

fn dock_mini(window: &WebviewWindow, side: &str) -> Result<(), String> {
    let Some(monitor) = window
        .current_monitor()
        .map_err(|error| format!("Could not read monitor: {error}"))?
    else {
        return Ok(());
    };

    let scale = monitor.scale_factor();
    let monitor_position = monitor.position().to_logical::<f64>(scale);
    let monitor_size = monitor.size().to_logical::<f64>(scale);
    let window_size = window
        .outer_size()
        .map_err(|error| format!("Could not read mini player size: {error}"))?
        .to_logical::<f64>(scale);

    let edge = 18.0;
    let taskbar_gap = 62.0;
    let x = if side == "left" {
        monitor_position.x + edge
    } else {
        monitor_position.x + monitor_size.width - window_size.width - edge
    };
    let y = monitor_position.y + monitor_size.height - window_size.height - taskbar_gap;

    window
        .set_position(LogicalPosition::new(x, y))
        .map_err(|error| format!("Could not dock mini player: {error}"))
}

fn clamp_mini_position(window: &WebviewWindow, x: f64, y: f64) -> Result<(f64, f64), String> {
    let Some(monitor) = window
        .current_monitor()
        .map_err(|error| format!("Could not read monitor: {error}"))?
    else {
        return Ok((x, y));
    };

    let scale = monitor.scale_factor();
    let monitor_position = monitor.position().to_logical::<f64>(scale);
    let monitor_size = monitor.size().to_logical::<f64>(scale);
    let window_size = window
        .outer_size()
        .map_err(|error| format!("Could not read mini player size: {error}"))?
        .to_logical::<f64>(scale);

    let edge = 8.0;
    let min_x = monitor_position.x + edge;
    let min_y = monitor_position.y + edge;
    let max_x = monitor_position.x + monitor_size.width - window_size.width - edge;
    let max_y = monitor_position.y + monitor_size.height - window_size.height - edge;

    Ok((x.clamp(min_x, max_x.max(min_x)), y.clamp(min_y, max_y.max(min_y))))
}

#[tauri::command]
fn open_mini_player(app: AppHandle, mode: String, dock: String) -> Result<(), String> {
    let (width, height, resizable) = mini_dimensions(&mode);

    let Some(window) = app.get_webview_window("mini") else {
        return Err("Mini player window is unavailable".to_string());
    };

    window
        .set_size(LogicalSize::new(width, height))
        .map_err(|error| format!("Could not resize mini player: {error}"))?;
    window
        .set_resizable(resizable)
        .map_err(|error| format!("Could not update mini player resize mode: {error}"))?;
    window
        .set_always_on_top(true)
        .map_err(|error| format!("Could not pin mini player: {error}"))?;
    window
        .show()
        .map_err(|error| format!("Could not show mini player: {error}"))?;
    window
        .set_focus()
        .map_err(|error| format!("Could not focus mini player: {error}"))?;

    if dock == "left" || dock == "right" {
        dock_mini(&window, &dock)?;
    }

    Ok(())
}

#[tauri::command]
fn set_mini_mode(window: WebviewWindow, mode: String) -> Result<(), String> {
    let (width, height, resizable) = mini_dimensions(&mode);
    window
        .set_size(LogicalSize::new(width, height))
        .map_err(|error| format!("Could not resize mini player: {error}"))?;
    window
        .set_resizable(resizable)
        .map_err(|error| format!("Could not update resize mode: {error}"))?;

    let position = window
        .outer_position()
        .map_err(|error| format!("Could not read mini player position: {error}"))?;
    let scale = window
        .scale_factor()
        .map_err(|error| format!("Could not read scale factor: {error}"))?;
    let logical = position.to_logical::<f64>(scale);
    let (x, y) = clamp_mini_position(&window, logical.x, logical.y)?;
    window
        .set_position(LogicalPosition::new(x, y))
        .map_err(|error| format!("Could not keep mini player visible: {error}"))
}

#[tauri::command]
fn start_mini_drag(window: WebviewWindow) -> Result<(), String> {
    window
        .start_dragging()
        .map_err(|error| format!("Could not drag mini player: {error}"))
}

#[tauri::command]
fn mini_position(window: WebviewWindow) -> Result<(f64, f64), String> {
    let position = window
        .outer_position()
        .map_err(|error| format!("Could not read mini player position: {error}"))?;
    let scale = window
        .scale_factor()
        .map_err(|error| format!("Could not read scale factor: {error}"))?;
    let logical = position.to_logical::<f64>(scale);
    Ok((logical.x, logical.y))
}

#[tauri::command]
fn set_mini_position(window: WebviewWindow, x: f64, y: f64) -> Result<(), String> {
    let (x, y) = clamp_mini_position(&window, x, y)?;
    window
        .set_position(LogicalPosition::new(x, y))
        .map_err(|error| format!("Could not restore mini player position: {error}"))
}

#[tauri::command]
fn set_mini_always_on_top(window: WebviewWindow, enabled: bool) -> Result<(), String> {
    window
        .set_always_on_top(enabled)
        .map_err(|error| format!("Could not update always-on-top: {error}"))
}

#[tauri::command]
fn dock_mini_player(window: WebviewWindow, side: String) -> Result<(), String> {
    dock_mini(&window, if side == "left" { "left" } else { "right" })
}

#[tauri::command]
fn hide_mini_player(window: WebviewWindow) -> Result<(), String> {
    window
        .hide()
        .map_err(|error| format!("Could not hide mini player: {error}"))
}

#[tauri::command]
fn show_main_window(app: AppHandle) -> Result<(), String> {
    let Some(window) = app.get_webview_window("main") else {
        return Err("Main vitr window is unavailable".to_string());
    };
    window
        .show()
        .map_err(|error| format!("Could not show vitr: {error}"))?;
    window
        .set_focus()
        .map_err(|error| format!("Could not focus vitr: {error}"))
}

pub fn run() {
    tauri::Builder::default()
        .invoke_handler(tauri::generate_handler![
            open_mini_player,
            set_mini_mode,
            start_mini_drag,
            mini_position,
            set_mini_position,
            set_mini_always_on_top,
            dock_mini_player,
            hide_mini_player,
            show_main_window
        ])
        .run(tauri::generate_context!())
        .expect("vitr failed to start");
}
