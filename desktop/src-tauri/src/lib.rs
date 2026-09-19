pub fn run() {
    tauri::Builder::default()
        .run(tauri::generate_context!())
        .expect("vitr failed to start");
}
