package id.local.wuwaconnect;

import android.app.AlertDialog;
import android.content.Context;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Multi-language UI strings with automatic system locale detection.
 */
public final class I18n {
    public static final String PREFS = "wuwaconnect_prefs";
    public static final String KEY_LANG = "app_lang";

    public static final String[] LANG_CODES = {
        "auto", "en", "id", "ms", "zh", "zh-TW", "ja", "ko", "vi", "th", "es", "pt", "fr", "de", "ru", "ar", "tr", "fil"
    };

    public static final String[] LANG_NAMES = {
        "Auto (System)", "English", "Bahasa Indonesia", "Bahasa Melayu",
        "中文 (简体)", "中文 (繁體)", "日本語", "한국어", "Tiếng Việt", "ไทย",
        "Español", "Português", "Français", "Deutsch", "Русский", "العربية", "Türkçe", "Filipino"
    };

    private static final Map<String, Map<String, String>> TABLE = new HashMap<>();

    static {
        put("en", new String[][]{
            {"tab_home", "Home"}, {"tab_conn", "Connection"}, {"tab_db", "Database"}, {"tab_sys", "System"}, {"tab_about", "About"},
            {"ready", "Ready"}, {"starting", "Starting"}, {"connected", "Connected"}, {"failed", "Failed"}, {"working", "Working"}, {"active", "Active"},
            {"start", "Start"}, {"starting_btn", "Starting…"}, {"stop", "Stop"}, {"grant", "Grant"}, {"root", "Root"},
            {"access", "Access"}, {"mode", "Mode"}, {"state", "State"}, {"uid", "UID"},
            {"connect_srv", "Connect Server"}, {"local_srv", "Local Server"}, {"mode_connect", "Connect"}, {"mode_local", "Local"},
            {"host_mode", "Host Mode"}, {"solo", "Solo"}, {"coop", "Co-op"},
            {"copy", "Copy"}, {"clear", "Clear"}, {"list", "List"}, {"paste", "Paste"}, {"add", "Add"},
            {"check", "Check"}, {"switch", "Switch"}, {"sync", "Sync"}, {"rebuild", "Rebuild"}, {"install", "Install"},
            {"allow", "Allow"}, {"open", "Open"}, {"next", "Next"}, {"back", "Back"}, {"cont", "Continue"}, {"recheck", "Re-check"},
            {"refresh", "Refresh"}, {"new_btn", "+ New"}, {"backup", "Backup"}, {"save", "Save"}, {"cancel", "Cancel"}, {"delete", "Delete"},
            {"edit", "Edit"}, {"restore", "Restore"},
            {"download", "Download"}, {"view", "View"}, {"change", "Change"}, {"lang_title", "Language"}, {"lang_auto", "Auto"},
            {"wiz_check_btn", "Check device"}, {"wiz_tagline", "Wuthering Waves 3.8 server emulator in Rust"},
            {"wiz_checking", "Device check"}, {"wiz_setup", "Setup"},
            {"setup_wiz", "Initial setup"}, {"setup_wiz_sub", "Device check, access & mode"},
            {"wiz_conn_sub", "Server IP or proxy link. No Rust compiler or assets.db needed."},
            {"wiz_loc_sub", "Solo or Co-op on device. Downloads server, assets.db & Rust."},
            {"wiz_ports_title", "Ports & Database"}, {"wiz_ports_sub", "5500 login, 8080 proxy, 13100 UDP game (KCP). Edits gamesv.db in /0/sonoro."},
            {"wiz_req_hdr", "Requirements by mode"},
            {"wiz_req_conn", "Shizuku or Root + VPN. No compiler or assets.db needed."},
            {"wiz_req_loc", "Downloads server source, assets.db & ARM64 Rust toolchain."},
            {"wiz_switch_note", "You can switch between Connect and Local anytime in Home or System."},
            {"wiz_cpu", "CPU & Android"}, {"wiz_arm64_req", "arm64-v8a required"}, {"wiz_arm64_bad", "Unsupported CPU architecture"},
            {"wiz_all_files_ok", "All Files Access granted"}, {"wiz_needed_local", "Needed for Local Server"},
            {"wiz_game_client", "Game client"}, {"wiz_install_wuwa", "Install Wuthering Waves"},
            {"wiz_priv_sub", "Patches Engine.ini and runs server"}, {"wiz_installed", "Installed"}, {"wiz_missing_pkg", "Not installed"},
            {"wiz_shizuku_ok", "Shizuku ready"}, {"wiz_shizuku_det", "Shizuku detected"}, {"wiz_shizuku_off", "Shizuku not running"},
            {"wiz_su_ok", "su available"}, {"wiz_no_su", "No su"},
            {"wiz_tap_chk", "No root · tap to check"}, {"wiz_no_root_rec", "No root · recommended"},
            {"wiz_tap_grant", "Magisk / KernelSU · tap to grant"}, {"wiz_conn_short", "Enter server IP or proxy · no assets.db needed"},
            {"wiz_loc_short", "Solo or Co-op · downloads server, assets.db & Rust"},
            {"upd_avail", "Update available"}, {"checking_gh", "Checking GitHub..."}, {"up_to_date", "Up to date"},
            {"tap_check", "Tap Check to look for updates"}, {"repo_launcher", "Launcher repository"}, {"repo_server", "Server repository"},
            {"app_ver", "App version"}, {"updates", "Updates"}, {"app_sec", "About"},
            {"version_lbl", "Version"}, {"developer_lbl", "Developer"}, {"build_ready", "Build %s ready"}, {"new_badge", "NEW"},
            {"log_title", "Server & Compiler Log"}, {"output", "Output"}, {"priv", "Privilege"}, {"priv_stor", "Privilege & Storage"},
            {"local_compiler", "Local Server & Compiler"}, {"log_lbl", "Log"},
            {"add_conn", "Add connection"}, {"saved", "Saved"}, {"no_saved_conns", "No saved connections"},
            {"conn_hint", "Paste hy2://, hysteria://, vless://, vmess://, trojan://, ss://, tuic://, wg://, socks5://, ip:port, sub, or JSON"},
            {"home_input_hint", "IP:Port, URI, or JSON"},
            {"selected", "Selected"}, {"clip_empty", "Clipboard empty"}, {"added", "Added"}, {"detecting", "Detecting..."},
            {"invalid_conn", "Invalid connection"}, {"paste_first", "Paste link or address first"},
            {"file_lbl", "File"}, {"players", "Players"}, {"backups", "Backups"}, {"new_player", "New Player"}, {"edit_player", "Edit Player"},
            {"uid_name", "UID & Name"}, {"auto_uid", "Auto UID"}, {"ul_wl", "Union Level & SOL3 World Level"},
            {"level_lbl", "Level"}, {"world_lv_lbl", "World Lv"}, {"credits_astrite", "Shell Credits & Astrite"},
            {"shell_credits", "Shell Credits"}, {"astrite", "Astrite"}, {"lunite_exp", "Lunite & EXP"}, {"lunite", "Lunite"},
            {"rover_sex", "Rover Sex"}, {"male", "Male"}, {"female", "Female"}, {"no_players", "No players in gamesv.db yet"},
            {"stor_perm_needed", "Storage permission needed"}, {"allow_storage", "Allow Storage"}, {"players_suffix", "players"},
            {"prep_local", "Preparing local server"}, {"start_vpn_proxy", "Starting VPN & proxy"},
            {"local_active_pfx", "Local active"}, {"vpn_active_pfx", "VPN active"}, {"open_sys_log", "Open System for log"},
            {"not_checked", "Not checked"}, {"no_server", "No server"}, {"detecting_pub_ip", "Detecting public IP..."},
            {"not_built", "Not built"}, {"waiting_start", "Waiting to start..."}, {"empty", "Empty"},
            {"remote_ip_sub", "Remote IP or proxy"}, {"engine_owner", "Engine.ini owner"},
            {"storage_dir", "Storage (/0/sonoro)"}, {"granted", "Granted"}, {"perm_needed", "Permission needed"},
            {"sync_assets_db", "Sync assets.json & DB"}, {"rust_compiler", "Rust compiler & build"}, {"not_installed", "Not installed"},
            {"no_root_chk", "No root · check or request"}, {"magisk_grant", "Magisk / KernelSU · grant root"},
            {"stor_needed_sub", "/0/sonoro · storage needed"}, {"assets_ok", "assets.db OK"}, {"assets_needed", "assets.db needed"},
            {"rust_ready", "Rust ready"}, {"auto_rust", "Auto-install Rust"},
            {"step_running", "Running"}, {"step_done", "Done"}, {"step_failed", "Failed"},
            {"step_c1", "Check connection"}, {"step_c2", "Patch Engine.ini"}, {"step_c3", "Start proxy"}, {"step_c4", "Launch game"},
            {"step_l1", "Sync assets.json & DB"}, {"step_l2", "Rust compiler & build"}, {"step_l3", "Start local server"}, {"step_l4", "Patch & launch game"}
        });

        put("id", new String[][]{
            {"tab_home", "Beranda"}, {"tab_conn", "Koneksi"}, {"tab_db", "Database"}, {"tab_sys", "Sistem"}, {"tab_about", "Tentang"},
            {"ready", "Siap"}, {"starting", "Memulai"}, {"connected", "Terhubung"}, {"failed", "Gagal"}, {"working", "Proses"}, {"active", "Aktif"},
            {"start", "Mulai"}, {"starting_btn", "Memulai…"}, {"stop", "Stop"}, {"grant", "Izin"}, {"root", "Root"},
            {"access", "Akses"}, {"mode", "Mode"}, {"state", "Status"}, {"uid", "UID"},
            {"connect_srv", "Connect Server"}, {"local_srv", "Local Server"}, {"mode_connect", "Connect"}, {"mode_local", "Lokal"},
            {"host_mode", "Mode Host"}, {"solo", "Solo"}, {"coop", "Co-op"},
            {"copy", "Salin"}, {"clear", "Hapus"}, {"list", "Daftar"}, {"paste", "Tempel"}, {"add", "Tambah"},
            {"check", "Cek"}, {"switch", "Ganti"}, {"sync", "Sinkron"}, {"rebuild", "Build Ulang"}, {"install", "Install"},
            {"allow", "Izinkan"}, {"open", "Buka"}, {"next", "Lanjut"}, {"back", "Kembali"}, {"cont", "Lanjutkan"}, {"recheck", "Cek Ulang"},
            {"refresh", "Segarkan"}, {"new_btn", "+ Baru"}, {"backup", "Cadangkan"}, {"save", "Simpan"}, {"cancel", "Batal"}, {"delete", "Hapus"},
            {"edit", "Ubah"}, {"restore", "Pulihkan"},
            {"download", "Unduh"}, {"view", "Lihat"}, {"change", "Ubah"}, {"lang_title", "Bahasa"}, {"lang_auto", "Otomatis"},
            {"wiz_check_btn", "Cek perangkat"}, {"wiz_tagline", "Emulator server Wuthering Waves 3.8 dalam Rust"},
            {"wiz_checking", "Cek perangkat"}, {"wiz_setup", "Pengaturan"},
            {"setup_wiz", "Pengaturan awal"}, {"setup_wiz_sub", "Cek perangkat, akses & mode"},
            {"wiz_conn_sub", "Masukkan IP server atau proxy. Tanpa compiler Rust atau assets.db."},
            {"wiz_loc_sub", "Main Solo atau Co-op di HP. Mengunduh server, assets.db & Rust."},
            {"wiz_ports_title", "Port & Database"}, {"wiz_ports_sub", "Login 5500, proxy 8080, game UDP 13100 (KCP). Mengelola gamesv.db di /0/sonoro."},
            {"wiz_req_hdr", "Syarat tiap mode"},
            {"wiz_req_conn", "Shizuku atau Root + VPN. Tidak perlu compiler atau assets.db."},
            {"wiz_req_loc", "Mengunduh source server, assets.db & toolchain Rust ARM64."},
            {"wiz_switch_note", "Mode Connect dan Local bisa diganti kapan saja di Beranda atau Sistem."},
            {"wiz_cpu", "CPU & Android"}, {"wiz_arm64_req", "Wajib arm64-v8a"}, {"wiz_arm64_bad", "Arsitektur CPU tidak didukung"},
            {"wiz_all_files_ok", "Izin Semua File aktif"}, {"wiz_needed_local", "Dibutuhkan untuk Local Server"},
            {"wiz_game_client", "Klien game"}, {"wiz_install_wuwa", "Install Wuthering Waves"},
            {"wiz_priv_sub", "Mengubah Engine.ini & menjalankan server"}, {"wiz_installed", "Terpasang"}, {"wiz_missing_pkg", "Belum terpasang"},
            {"wiz_shizuku_ok", "Shizuku siap"}, {"wiz_shizuku_det", "Shizuku terdeteksi"}, {"wiz_shizuku_off", "Shizuku tidak aktif"},
            {"wiz_su_ok", "su tersedia"}, {"wiz_no_su", "Tanpa su"},
            {"wiz_tap_chk", "Tanpa root · ketuk untuk cek"}, {"wiz_no_root_rec", "Tanpa root · disarankan"},
            {"wiz_tap_grant", "Magisk / KernelSU · ketuk untuk izin"}, {"wiz_conn_short", "Masukkan IP server atau proxy · tanpa assets.db"},
            {"wiz_loc_short", "Solo atau Co-op · unduh server, assets.db & Rust"},
            {"upd_avail", "Pembaruan tersedia"}, {"checking_gh", "Memeriksa GitHub..."}, {"up_to_date", "Sudah terbaru"},
            {"tap_check", "Ketuk Cek untuk memeriksa pembaruan"}, {"repo_launcher", "Repositori Launcher"}, {"repo_server", "Repositori Server"},
            {"app_ver", "Versi aplikasi"}, {"updates", "Pembaruan"}, {"app_sec", "Tentang"},
            {"version_lbl", "Versi"}, {"developer_lbl", "Pengembang"}, {"build_ready", "Build %s siap"}, {"new_badge", "BARU"},
            {"log_title", "Log Server & Compiler"}, {"output", "Output"}, {"priv", "Hak Akses"}, {"priv_stor", "Hak Akses & Penyimpanan"},
            {"local_compiler", "Server Lokal & Compiler"}, {"log_lbl", "Log"},
            {"add_conn", "Tambah koneksi"}, {"saved", "Tersimpan"}, {"no_saved_conns", "Belum ada koneksi tersimpan"},
            {"conn_hint", "Tempel hy2://, hysteria://, vless://, vmess://, trojan://, ss://, tuic://, wg://, socks5://, ip:port, sub, atau JSON"},
            {"home_input_hint", "IP:Port, URI, atau JSON"},
            {"selected", "Dipilih"}, {"clip_empty", "Papan klip kosong"}, {"added", "Ditambahkan"}, {"detecting", "Mendeteksi..."},
            {"invalid_conn", "Koneksi tidak valid"}, {"paste_first", "Tempel link atau alamat terlebih dahulu"},
            {"file_lbl", "File"}, {"players", "Pemain"}, {"backups", "Cadangan"}, {"new_player", "Pemain Baru"}, {"edit_player", "Edit Pemain"},
            {"uid_name", "UID & Nama"}, {"auto_uid", "UID Otomatis"}, {"ul_wl", "Union Level & SOL3 World Level"},
            {"level_lbl", "Level"}, {"world_lv_lbl", "Level Dunia"}, {"credits_astrite", "Shell Credits & Astrite"},
            {"shell_credits", "Shell Credits"}, {"astrite", "Astrite"}, {"lunite_exp", "Lunite & EXP"}, {"lunite", "Lunite"},
            {"rover_sex", "Jenis Kelamin Rover"}, {"male", "Pria"}, {"female", "Wanita"}, {"no_players", "Belum ada pemain di gamesv.db"},
            {"stor_perm_needed", "Izin penyimpanan diperlukan"}, {"allow_storage", "Izinkan Penyimpanan"}, {"players_suffix", "pemain"},
            {"prep_local", "Menyiapkan server lokal"}, {"start_vpn_proxy", "Menjalankan VPN & proxy"},
            {"local_active_pfx", "Lokal aktif"}, {"vpn_active_pfx", "VPN aktif"}, {"open_sys_log", "Buka Sistem untuk melihat log"},
            {"not_checked", "Belum dicek"}, {"no_server", "Belum ada server"}, {"detecting_pub_ip", "Mendeteksi IP publik..."},
            {"not_built", "Belum dibuild"}, {"waiting_start", "Menunggu mulai..."}, {"empty", "Kosong"},
            {"remote_ip_sub", "IP atau proxy jarak jauh"}, {"engine_owner", "Pemilik Engine.ini"},
            {"storage_dir", "Penyimpanan (/0/sonoro)"}, {"granted", "Diizinkan"}, {"perm_needed", "Butuh izin"},
            {"sync_assets_db", "Sinkron assets.json & DB"}, {"rust_compiler", "Compiler & build Rust"}, {"not_installed", "Belum terpasang"},
            {"no_root_chk", "Tanpa root · cek atau minta izin"}, {"magisk_grant", "Magisk / KernelSU · izin root"},
            {"stor_needed_sub", "/0/sonoro · butuh izin penyimpanan"}, {"assets_ok", "assets.db OK"}, {"assets_needed", "assets.db belum ada"},
            {"rust_ready", "Rust siap"}, {"auto_rust", "Install otomatis Rust"},
            {"step_running", "Berjalan"}, {"step_done", "Selesai"}, {"step_failed", "Gagal"},
            {"step_c1", "Cek koneksi"}, {"step_c2", "Patch Engine.ini"}, {"step_c3", "Mulai proxy"}, {"step_c4", "Jalankan game"},
            {"step_l1", "Sinkron assets.json & DB"}, {"step_l2", "Compiler & build Rust"}, {"step_l3", "Mulai server lokal"}, {"step_l4", "Patch & buka game"}
        });

        put("ms", new String[][]{
            {"tab_home", "Utama"}, {"tab_conn", "Sambungan"}, {"tab_db", "Pangkalan Data"}, {"tab_sys", "Sistem"}, {"tab_about", "Perihal"},
            {"ready", "Sedia"}, {"starting", "Memulakan"}, {"connected", "Bersambung"}, {"failed", "Gagal"}, {"working", "Berjalan"}, {"active", "Aktif"},
            {"start", "Mula"}, {"starting_btn", "Memulakan…"}, {"stop", "Henti"}, {"grant", "Beri"}, {"root", "Root"},
            {"access", "Akses"}, {"mode", "Mod"}, {"state", "Status"}, {"uid", "UID"},
            {"connect_srv", "Connect Server"}, {"local_srv", "Local Server"}, {"mode_connect", "Sambung"}, {"mode_local", "Tempatan"},
            {"host_mode", "Mod Hos"}, {"solo", "Solo"}, {"coop", "Co-op"},
            {"copy", "Salin"}, {"clear", "Padam"}, {"list", "Senarai"}, {"paste", "Tampal"}, {"add", "Tambah"},
            {"check", "Semak"}, {"switch", "Tukar"}, {"sync", "Segerak"}, {"rebuild", "Bina Semula"}, {"install", "Pasang"},
            {"allow", "Benarkan"}, {"open", "Buka"}, {"next", "Seterusnya"}, {"back", "Kembali"}, {"cont", "Teruskan"}, {"recheck", "Semak Semula"},
            {"refresh", "Segar"}, {"new_btn", "+ Baharu"}, {"backup", "Sandaran"}, {"save", "Simpan"}, {"cancel", "Batal"}, {"delete", "Padam"},
            {"edit", "Sunting"}, {"restore", "Pulihkan"},
            {"download", "Muat Turun"}, {"view", "Lihat"}, {"change", "Tukar"}, {"lang_title", "Bahasa"}, {"lang_auto", "Auto"},
            {"wiz_check_btn", "Semak peranti"}, {"wiz_tagline", "Emulator pelayan Wuthering Waves 3.8 dalam Rust"},
            {"wiz_checking", "Semak peranti"}, {"wiz_setup", "Persediaan"},
            {"setup_wiz", "Persediaan awal"}, {"setup_wiz_sub", "Semakan peranti, akses & mod"},
            {"wiz_conn_sub", "Masukkan IP pelayan atau proksi. Tiada pengkompil Rust atau assets.db diperlukan."},
            {"wiz_loc_sub", "Solo atau Co-op pada peranti. Muat turun pelayan, assets.db & Rust."},
            {"wiz_ports_title", "Port & Pangkalan Data"}, {"wiz_ports_sub", "5500 log masuk, 8080 proksi, 13100 UDP permainan. Sunting gamesv.db dalam /0/sonoro."},
            {"wiz_req_hdr", "Keperluan mengikut mod"},
            {"wiz_req_conn", "Shizuku atau Root + VPN. Tiada pengkompil atau assets.db diperlukan."},
            {"wiz_req_loc", "Muat turun sumber pelayan, assets.db & alat Rust ARM64."},
            {"wiz_switch_note", "Anda boleh menukar Connect dan Local bila-bila masa di Utama atau Sistem."},
            {"wiz_cpu", "CPU & Android"}, {"wiz_arm64_req", "arm64-v8a diperlukan"}, {"wiz_arm64_bad", "Seni bina CPU tidak disokong"},
            {"wiz_all_files_ok", "Akses Semua Fail dibenarkan"}, {"wiz_needed_local", "Diperlukan untuk Local Server"},
            {"wiz_game_client", "Klien permainan"}, {"wiz_install_wuwa", "Pasang Wuthering Waves"},
            {"wiz_priv_sub", "Menampal Engine.ini & menjalankan pelayan"}, {"wiz_installed", "Dipasang"}, {"wiz_missing_pkg", "Tiada"},
            {"wiz_shizuku_ok", "Shizuku sedia"}, {"wiz_shizuku_det", "Shizuku dikesan"}, {"wiz_shizuku_off", "Shizuku tidak aktif"},
            {"wiz_su_ok", "su tersedia"}, {"wiz_no_su", "Tiada su"},
            {"wiz_tap_chk", "Tiada root · ketik untuk semak"}, {"wiz_no_root_rec", "Tiada root · disyorkan"},
            {"wiz_tap_grant", "Magisk / KernelSU · ketik untuk izin"}, {"wiz_conn_short", "IP pelayan atau proksi · tanpa assets.db"},
            {"wiz_loc_short", "Solo atau Co-op · muat turun pelayan, assets.db & Rust"},
            {"upd_avail", "Kemas Kini Tersedia"}, {"checking_gh", "Menyemak GitHub..."}, {"up_to_date", "Terkini"},
            {"tap_check", "Ketik Semak untuk mencari kemas kini"}, {"repo_launcher", "Repositori Pelancar"}, {"repo_server", "Repositori Pelayan"},
            {"app_ver", "Versi aplikasi"}, {"updates", "Kemas Kini"}, {"app_sec", "Perihal"},
            {"version_lbl", "Versi"}, {"developer_lbl", "Pembangun"}, {"build_ready", "Binaan %s sedia"}, {"new_badge", "BAHARU"},
            {"log_title", "Log Pelayan & Pengkompil"}, {"output", "Output"}, {"priv", "Keistimewaan"}, {"priv_stor", "Keistimewaan & Storan"},
            {"local_compiler", "Pelayan Tempatan & Pengkompil"}, {"log_lbl", "Log"},
            {"add_conn", "Tambah sambungan"}, {"saved", "Disimpan"}, {"no_saved_conns", "Tiada sambungan disimpan"},
            {"conn_hint", "Tampal hy2://, vless://, vmess://, trojan://, ss://, ip:port atau JSON"},
            {"home_input_hint", "IP:Port, URI atau JSON"},
            {"selected", "Dipilih"}, {"clip_empty", "Papan klip kosong"}, {"added", "Ditambah"}, {"detecting", "Mengesan..."},
            {"invalid_conn", "Sambungan tidak sah"}, {"paste_first", "Tampal pautan atau alamat dahulu"},
            {"file_lbl", "Fail"}, {"players", "Pemain"}, {"backups", "Sandaran"}, {"new_player", "Pemain Baharu"}, {"edit_player", "Sunting Pemain"},
            {"uid_name", "UID & Nama"}, {"auto_uid", "UID Auto"}, {"ul_wl", "Union Level & SOL3 World Level"},
            {"level_lbl", "Tahap"}, {"world_lv_lbl", "Tahap Dunia"}, {"credits_astrite", "Shell Credits & Astrite"},
            {"shell_credits", "Shell Credits"}, {"astrite", "Astrite"}, {"lunite_exp", "Lunite & EXP"}, {"lunite", "Lunite"},
            {"rover_sex", "Jantina Rover"}, {"male", "Lelaki"}, {"female", "Perempuan"}, {"no_players", "Tiada pemain dalam gamesv.db"},
            {"stor_perm_needed", "Kebenaran storan diperlukan"}, {"allow_storage", "Benarkan Storan"}, {"players_suffix", "pemain"},
            {"prep_local", "Menyediakan pelayan tempatan"}, {"start_vpn_proxy", "Memulakan VPN & proksi"},
            {"local_active_pfx", "Tempatan aktif"}, {"vpn_active_pfx", "VPN aktif"}, {"open_sys_log", "Buka Sistem untuk log"},
            {"not_checked", "Belum disemak"}, {"no_server", "Tiada pelayan"}, {"detecting_pub_ip", "Mengesan IP awam..."},
            {"not_built", "Belum dibina"}, {"waiting_start", "Menunggu mula..."}, {"empty", "Kosong"},
            {"remote_ip_sub", "IP atau proksi jauh"}, {"engine_owner", "Pemilik Engine.ini"},
            {"storage_dir", "Storan (/0/sonoro)"}, {"granted", "Dibenarkan"}, {"perm_needed", "Kebenaran diperlukan"},
            {"sync_assets_db", "Segerak assets.json & DB"}, {"rust_compiler", "Pengkompil & binaan Rust"}, {"not_installed", "Belum dipasang"},
            {"no_root_chk", "Tiada root · semak atau minta"}, {"magisk_grant", "Magisk / KernelSU · izin root"},
            {"stor_needed_sub", "/0/sonoro · storan diperlukan"}, {"assets_ok", "assets.db OK"}, {"assets_needed", "assets.db diperlukan"},
            {"rust_ready", "Rust sedia"}, {"auto_rust", "Pasang automatik Rust"},
            {"step_running", "Berjalan"}, {"step_done", "Selesai"}, {"step_failed", "Gagal"},
            {"step_c1", "Semak sambungan"}, {"step_c2", "Tampal Engine.ini"}, {"step_c3", "Mula proksi"}, {"step_c4", "Lancarkan permainan"},
            {"step_l1", "Segerak assets.json & DB"}, {"step_l2", "Pengkompil & binaan Rust"}, {"step_l3", "Mula pelayan tempatan"}, {"step_l4", "Tampal & buka permainan"}
        });

        put("zh", new String[][]{
            {"tab_home", "主页"}, {"tab_conn", "连接"}, {"tab_db", "数据库"}, {"tab_sys", "系统"}, {"tab_about", "关于"},
            {"ready", "就绪"}, {"starting", "启动中"}, {"connected", "已连接"}, {"failed", "失败"}, {"working", "处理中"}, {"active", "运行中"},
            {"start", "启动"}, {"starting_btn", "启动中…"}, {"stop", "停止"}, {"grant", "授权"}, {"root", "Root"},
            {"access", "权限"}, {"mode", "模式"}, {"state", "状态"}, {"uid", "UID"},
            {"connect_srv", "连接服务器"}, {"local_srv", "本地服务器"}, {"mode_connect", "远程"}, {"mode_local", "本地"},
            {"host_mode", "主机模式"}, {"solo", "单人"}, {"coop", "联机"},
            {"copy", "复制"}, {"clear", "清空"}, {"list", "列表"}, {"paste", "粘贴"}, {"add", "添加"},
            {"check", "检查"}, {"switch", "切换"}, {"sync", "同步"}, {"rebuild", "重新编译"}, {"install", "安装"},
            {"allow", "允许"}, {"open", "打开"}, {"next", "下一步"}, {"back", "返回"}, {"cont", "继续"}, {"recheck", "重新检查"},
            {"refresh", "刷新"}, {"new_btn", "+ 新建"}, {"backup", "备份"}, {"save", "保存"}, {"cancel", "取消"}, {"delete", "删除"},
            {"edit", "编辑"}, {"restore", "恢复"},
            {"download", "下载"}, {"view", "查看"}, {"change", "更改"}, {"lang_title", "语言"}, {"lang_auto", "自动"},
            {"wiz_check_btn", "检查设备"}, {"wiz_tagline", "Rust 编写的鸣潮 3.8 服务端模拟器"},
            {"wiz_checking", "检查设备"}, {"wiz_setup", "初始设置"},
            {"setup_wiz", "初始设置"}, {"setup_wiz_sub", "设备检测、权限与模式"},
            {"wiz_conn_sub", "填写服务器 IP 或代理链接，无需 Rust 编译器或 assets.db。"},
            {"wiz_loc_sub", "在本机单人或联机运行，自动下载服务端、assets.db 与 Rust。"},
            {"wiz_ports_title", "端口与数据库"}, {"wiz_ports_sub", "5500 登录、8080 代理、13100 UDP (KCP)。读写 /0/sonoro 中的 gamesv.db。"},
            {"wiz_req_hdr", "各模式要求"},
            {"wiz_req_conn", "Shizuku 或 Root + VPN，无需编译器或 assets.db。"},
            {"wiz_req_loc", "下载服务端源码、assets.db 及 ARM64 Rust 工具链。"},
            {"wiz_switch_note", "你随时可以在主页或系统中切换连接模式与本地模式。"},
            {"wiz_cpu", "CPU 与系统"}, {"wiz_arm64_req", "需要 arm64-v8a"}, {"wiz_arm64_bad", "不支持的 CPU 架构"},
            {"wiz_all_files_ok", "已授予所有文件权限"}, {"wiz_needed_local", "本地服务端所需权限"},
            {"wiz_game_client", "游戏客户端"}, {"wiz_install_wuwa", "请先安装鸣潮"},
            {"wiz_priv_sub", "修补 Engine.ini 并运行服务端"}, {"wiz_installed", "已安装"}, {"wiz_missing_pkg", "未安装"},
            {"wiz_shizuku_ok", "Shizuku 就绪"}, {"wiz_shizuku_det", "已检测到 Shizuku"}, {"wiz_shizuku_off", "Shizuku 未运行"},
            {"wiz_su_ok", "存在 su"}, {"wiz_no_su", "无 su"},
            {"wiz_tap_chk", "免 Root · 点击检测"}, {"wiz_no_root_rec", "免 Root · 推荐"},
            {"wiz_tap_grant", "Magisk / KernelSU · 点击授权"}, {"wiz_conn_short", "输入 IP 或代理 · 无需 assets.db"},
            {"wiz_loc_short", "单人或联机 · 自动下载服务端、assets.db 与 Rust"},
            {"upd_avail", "发现新版本"}, {"checking_gh", "正在检查 GitHub..."}, {"up_to_date", "已是最新"},
            {"tap_check", "点击检查以获取更新"}, {"repo_launcher", "启动器仓库"}, {"repo_server", "服务端仓库"},
            {"app_ver", "应用版本"}, {"updates", "更新"}, {"app_sec", "关于"},
            {"version_lbl", "版本"}, {"developer_lbl", "开发者"}, {"build_ready", "构建 %s 就绪"}, {"new_badge", "新版"},
            {"log_title", "服务端与编译日志"}, {"output", "输出"}, {"priv", "权限"}, {"priv_stor", "权限与存储"},
            {"local_compiler", "本地服务端与编译器"}, {"log_lbl", "日志"},
            {"add_conn", "添加连接"}, {"saved", "已保存"}, {"no_saved_conns", "暂无保存的连接"},
            {"conn_hint", "粘贴 hy2://、vless://、vmess://、trojan://、ss://、ip:port、订阅或 JSON"},
            {"home_input_hint", "IP:端口、URI 或 JSON"},
            {"selected", "已选中"}, {"clip_empty", "剪贴板为空"}, {"added", "已添加"}, {"detecting", "识别中..."},
            {"invalid_conn", "连接格式无效"}, {"paste_first", "请先粘贴链接或地址"},
            {"file_lbl", "文件"}, {"players", "玩家"}, {"backups", "备份"}, {"new_player", "新建玩家"}, {"edit_player", "编辑玩家"},
            {"uid_name", "UID 与名称"}, {"auto_uid", "自动 UID"}, {"ul_wl", "联觉等级与索拉等级"},
            {"level_lbl", "等级"}, {"world_lv_lbl", "世界等级"}, {"credits_astrite", "贝币与星声"},
            {"shell_credits", "贝币"}, {"astrite", "星声"}, {"lunite_exp", "月相与经验"}, {"lunite", "月相"},
            {"rover_sex", "漂泊者性别"}, {"male", "男"}, {"female", "女"}, {"no_players", "gamesv.db 中暂无玩家"},
            {"stor_perm_needed", "需要存储权限"}, {"allow_storage", "允许存储权限"}, {"players_suffix", "名玩家"},
            {"prep_local", "正在准备本地服务端"}, {"start_vpn_proxy", "正在启动 VPN 与代理"},
            {"local_active_pfx", "本地运行中"}, {"vpn_active_pfx", "VPN 运行中"}, {"open_sys_log", "前往系统查看日志"},
            {"not_checked", "未检查"}, {"no_server", "未设置服务器"}, {"detecting_pub_ip", "正在检测公网 IP..."},
            {"not_built", "未编译"}, {"waiting_start", "等待启动..."}, {"empty", "空"},
            {"remote_ip_sub", "远程 IP 或代理"}, {"engine_owner", "Engine.ini 所有者"},
            {"storage_dir", "存储 (/0/sonoro)"}, {"granted", "已授权"}, {"perm_needed", "需要权限"},
            {"sync_assets_db", "同步 assets.json 与数据库"}, {"rust_compiler", "Rust 编译器与构建"}, {"not_installed", "未安装"},
            {"no_root_chk", "免 Root · 检查或请求授权"}, {"magisk_grant", "Magisk / KernelSU · 授予 Root"},
            {"stor_needed_sub", "/0/sonoro · 需要存储权限"}, {"assets_ok", "assets.db 就绪"}, {"assets_needed", "缺少 assets.db"},
            {"rust_ready", "Rust 就绪"}, {"auto_rust", "自动安装 Rust"},
            {"step_running", "进行中"}, {"step_done", "完成"}, {"step_failed", "失败"},
            {"step_c1", "检查连接"}, {"step_c2", "修补 Engine.ini"}, {"step_c3", "启动代理"}, {"step_c4", "启动游戏"},
            {"step_l1", "同步 assets.json 与数据库"}, {"step_l2", "Rust 编译器与构建"}, {"step_l3", "启动本地服务端"}, {"step_l4", "修补并启动游戏"}
        });

        put("zh-TW", new String[][]{
            {"tab_home", "首頁"}, {"tab_conn", "連線"}, {"tab_db", "資料庫"}, {"tab_sys", "系統"}, {"tab_about", "關於"},
            {"ready", "就緒"}, {"starting", "啟動中"}, {"connected", "已連線"}, {"failed", "失敗"}, {"working", "處理中"}, {"active", "執行中"},
            {"start", "啟動"}, {"starting_btn", "啟動中…"}, {"stop", "停止"}, {"grant", "授權"}, {"root", "Root"},
            {"access", "權限"}, {"mode", "模式"}, {"state", "狀態"}, {"uid", "UID"},
            {"connect_srv", "連線伺服器"}, {"local_srv", "本機伺服器"}, {"mode_connect", "遠端"}, {"mode_local", "本機"},
            {"host_mode", "主機模式"}, {"solo", "單人"}, {"coop", "連線"},
            {"copy", "複製"}, {"clear", "清除"}, {"list", "清單"}, {"paste", "貼上"}, {"add", "新增"},
            {"check", "檢查"}, {"switch", "切換"}, {"sync", "同步"}, {"rebuild", "重新編譯"}, {"install", "安裝"},
            {"allow", "允許"}, {"open", "開啟"}, {"next", "下一步"}, {"back", "返回"}, {"cont", "繼續"}, {"recheck", "重新檢查"},
            {"refresh", "重新整理"}, {"new_btn", "+ 新增"}, {"backup", "備份"}, {"save", "儲存"}, {"cancel", "取消"}, {"delete", "刪除"},
            {"edit", "編輯"}, {"restore", "還原"},
            {"download", "下載"}, {"view", "檢視"}, {"change", "變更"}, {"lang_title", "語言"}, {"lang_auto", "自動"},
            {"wiz_check_btn", "檢查裝置"}, {"wiz_tagline", "以 Rust 編寫的鳴潮 3.8 伺服器模擬器"},
            {"wiz_checking", "檢查裝置"}, {"wiz_setup", "設定"},
            {"setup_wiz", "初始設定"}, {"setup_wiz_sub", "裝置檢測、權限與模式"},
            {"wiz_conn_sub", "輸入伺服器 IP 或代理連結，無需 Rust 編譯器或 assets.db。"},
            {"wiz_loc_sub", "於手機單人或連線遊玩，自動下載伺服器、assets.db 與 Rust。"},
            {"wiz_ports_title", "連接埠與資料庫"}, {"wiz_ports_sub", "5500 登入、8080 代理、13100 UDP (KCP)。讀寫 /0/sonoro 中的 gamesv.db。"},
            {"wiz_req_hdr", "各模式需求"},
            {"wiz_req_conn", "Shizuku 或 Root + VPN，無需編譯器或 assets.db。"},
            {"wiz_req_loc", "下載伺服器原始碼、assets.db 及 ARM64 Rust 工具鏈。"},
            {"wiz_switch_note", "隨時可在首頁或系統中切換連線與本機模式。"},
            {"wiz_cpu", "CPU 與系統"}, {"wiz_arm64_req", "需要 arm64-v8a"}, {"wiz_arm64_bad", "不支援的 CPU 架構"},
            {"wiz_all_files_ok", "已授予所有檔案權限"}, {"wiz_needed_local", "本機伺服器所需"},
            {"wiz_game_client", "遊戲用戶端"}, {"wiz_install_wuwa", "請安裝鳴潮"},
            {"wiz_priv_sub", "修補 Engine.ini 並執行伺服器"}, {"wiz_installed", "已安裝"}, {"wiz_missing_pkg", "未安裝"},
            {"wiz_shizuku_ok", "Shizuku 就緒"}, {"wiz_shizuku_det", "已偵測 Shizuku"}, {"wiz_shizuku_off", "Shizuku 未執行"},
            {"wiz_su_ok", "已有 su"}, {"wiz_no_su", "無 su"},
            {"wiz_tap_chk", "免 Root · 點擊檢查"}, {"wiz_no_root_rec", "免 Root · 建議"},
            {"wiz_tap_grant", "Magisk / KernelSU · 點擊授權"}, {"wiz_conn_short", "輸入 IP 或代理 · 無需 assets.db"},
            {"wiz_loc_short", "單人或連線 · 下載伺服器、assets.db 與 Rust"},
            {"upd_avail", "有可用更新"}, {"checking_gh", "正在檢查 GitHub..."}, {"up_to_date", "已是最新"},
            {"tap_check", "點擊檢查以查看更新"}, {"repo_launcher", "啟動器儲存庫"}, {"repo_server", "伺服器儲存庫"},
            {"app_ver", "應用程式版本"}, {"updates", "更新"}, {"app_sec", "關於"},
            {"version_lbl", "版本"}, {"developer_lbl", "開發者"}, {"build_ready", "建置 %s 就緒"}, {"new_badge", "新版"},
            {"log_title", "伺服器與編譯日誌"}, {"output", "輸出"}, {"priv", "權限"}, {"priv_stor", "權限與儲存空間"},
            {"local_compiler", "本機伺服器與編譯器"}, {"log_lbl", "日誌"},
            {"add_conn", "新增連線"}, {"saved", "已儲存"}, {"no_saved_conns", "尚無儲存的連線"},
            {"conn_hint", "貼上 hy2://、vless://、vmess://、trojan://、ss://、ip:port 或 JSON"},
            {"home_input_hint", "IP:連接埠、URI 或 JSON"},
            {"selected", "已選取"}, {"clip_empty", "剪貼簿是空的"}, {"added", "已新增"}, {"detecting", "偵測中..."},
            {"invalid_conn", "無效的連線"}, {"paste_first", "請先貼上連結或位址"},
            {"file_lbl", "檔案"}, {"players", "玩家"}, {"backups", "備份"}, {"new_player", "新增玩家"}, {"edit_player", "編輯玩家"},
            {"uid_name", "UID 與名稱"}, {"auto_uid", "自動 UID"}, {"ul_wl", "聯覺等級與索拉等級"},
            {"level_lbl", "等級"}, {"world_lv_lbl", "世界等級"}, {"credits_astrite", "貝幣與星聲"},
            {"shell_credits", "貝幣"}, {"astrite", "星聲"}, {"lunite_exp", "月相與經驗"}, {"lunite", "月相"},
            {"rover_sex", "漂泊者性別"}, {"male", "男"}, {"female", "女"}, {"no_players", "gamesv.db 尚無玩家"},
            {"stor_perm_needed", "需要儲存空間權限"}, {"allow_storage", "允許儲存權限"}, {"players_suffix", "位玩家"},
            {"prep_local", "正在準備本機伺服器"}, {"start_vpn_proxy", "正在啟動 VPN 與代理"},
            {"local_active_pfx", "本機執行中"}, {"vpn_active_pfx", "VPN 執行中"}, {"open_sys_log", "前往系統查看日誌"},
            {"not_checked", "尚未檢查"}, {"no_server", "尚未設定伺服器"}, {"detecting_pub_ip", "正在偵測公網 IP..."},
            {"not_built", "尚未編譯"}, {"waiting_start", "等待啟動..."}, {"empty", "空"},
            {"remote_ip_sub", "遠端 IP 或代理"}, {"engine_owner", "Engine.ini 擁有者"},
            {"storage_dir", "儲存空間 (/0/sonoro)"}, {"granted", "已授權"}, {"perm_needed", "需要權限"},
            {"sync_assets_db", "同步 assets.json 與資料庫"}, {"rust_compiler", "Rust 編譯器與建置"}, {"not_installed", "未安裝"},
            {"no_root_chk", "免 Root · 檢查或授權"}, {"magisk_grant", "Magisk / KernelSU · 授予 Root"},
            {"stor_needed_sub", "/0/sonoro · 需要儲存權限"}, {"assets_ok", "assets.db 正常"}, {"assets_needed", "缺少 assets.db"},
            {"rust_ready", "Rust 就緒"}, {"auto_rust", "自動安裝 Rust"},
            {"step_running", "執行中"}, {"step_done", "完成"}, {"step_failed", "失敗"},
            {"step_c1", "檢查連線"}, {"step_c2", "修補 Engine.ini"}, {"step_c3", "啟動代理"}, {"step_c4", "啟動遊戲"},
            {"step_l1", "同步 assets.json 與資料庫"}, {"step_l2", "Rust 編譯器與建置"}, {"step_l3", "啟動本機伺服器"}, {"step_l4", "修補並啟動遊戲"}
        });

        put("ja", new String[][]{
            {"tab_home", "ホーム"}, {"tab_conn", "接続"}, {"tab_db", "DB"}, {"tab_sys", "システム"}, {"tab_about", "情報"},
            {"ready", "待機"}, {"starting", "起動中"}, {"connected", "接続中"}, {"failed", "失敗"}, {"working", "処理中"}, {"active", "稼働中"},
            {"start", "開始"}, {"starting_btn", "起動中…"}, {"stop", "停止"}, {"grant", "許可"}, {"root", "Root"},
            {"access", "権限"}, {"mode", "モード"}, {"state", "状態"}, {"uid", "UID"},
            {"connect_srv", "接続サーバー"}, {"local_srv", "ローカルサーバー"}, {"mode_connect", "接続"}, {"mode_local", "ローカル"},
            {"host_mode", "ホスト"}, {"solo", "ソロ"}, {"coop", "マルチ"},
            {"copy", "コピー"}, {"clear", "消去"}, {"list", "一覧"}, {"paste", "貼付"}, {"add", "追加"},
            {"check", "確認"}, {"switch", "切替"}, {"sync", "同期"}, {"rebuild", "再ビルド"}, {"install", "導入"},
            {"allow", "許可"}, {"open", "開く"}, {"next", "次へ"}, {"back", "戻る"}, {"cont", "完了"}, {"recheck", "再確認"},
            {"refresh", "更新"}, {"new_btn", "+ 新規"}, {"backup", "保存"}, {"save", "保存"}, {"cancel", "取消"}, {"delete", "削除"},
            {"edit", "編集"}, {"restore", "復元"},
            {"download", "ダウンロード"}, {"view", "表示"}, {"change", "変更"}, {"lang_title", "言語"}, {"lang_auto", "自動"},
            {"wiz_check_btn", "端末をチェック"}, {"wiz_tagline", "Rust 製の鳴潮 3.8 サーバーエミュレータ"},
            {"wiz_checking", "端末チェック"}, {"wiz_setup", "セットアップ"},
            {"setup_wiz", "初期設定"}, {"setup_wiz_sub", "端末確認・権限・モード"},
            {"wiz_conn_sub", "サーバー IP やプロキシを入力。Rust や assets.db は不要です。"},
            {"wiz_loc_sub", "端末上でソロ/マルチを実行。サーバー、assets.db、Rust を取得します。"},
            {"wiz_ports_title", "ポート＆DB"}, {"wiz_ports_sub", "5500 ログイン / 8080 プロキシ / 13100 UDP。/0/sonoro の gamesv.db を編集。"},
            {"wiz_req_hdr", "モード別要件"},
            {"wiz_req_conn", "Shizuku または Root + VPN。コンパイラや assets.db は不要です。"},
            {"wiz_req_loc", "サーバーソース、assets.db、ARM64 Rust をダウンロードします。"},
            {"wiz_switch_note", "ホームまたはシステムからいつでもモードを切り替えられます。"},
            {"upd_avail", "アップデートあり"}, {"checking_gh", "GitHub を確認中..."}, {"up_to_date", "最新です"},
            {"tap_check", "確認をタップして更新をチェック"}, {"repo_launcher", "ランチャー"}, {"repo_server", "サーバー"},
            {"app_ver", "アプリバージョン"}, {"updates", "アップデート"}, {"app_sec", "情報"},
            {"log_title", "サーバー・コンパイルログ"}, {"output", "出力"}, {"priv", "権限"}, {"priv_stor", "権限とストレージ"},
            {"local_compiler", "ローカルサーバー & コンパイラ"}, {"log_lbl", "ログ"},
            {"add_conn", "接続を追加"}, {"saved", "保存済み"}, {"no_saved_conns", "保存された接続はありません"},
            {"selected", "選択済み"}, {"added", "追加しました"}, {"waiting_start", "開始待機中..."}, {"empty", "空"},
            {"granted", "許可済み"}, {"perm_needed", "権限が必要"}, {"players", "プレイヤー"}, {"backups", "バックアップ"}
        });

        put("ko", new String[][]{
            {"tab_home", "홈"}, {"tab_conn", "연결"}, {"tab_db", "DB"}, {"tab_sys", "시스템"}, {"tab_about", "정보"},
            {"ready", "준비"}, {"starting", "시작 중"}, {"connected", "연결됨"}, {"failed", "실패"}, {"working", "작업 중"}, {"active", "활성"},
            {"start", "시작"}, {"starting_btn", "시작 중…"}, {"stop", "중지"}, {"grant", "허용"}, {"root", "Root"},
            {"access", "권한"}, {"mode", "모드"}, {"state", "상태"}, {"uid", "UID"},
            {"connect_srv", "연결 서버"}, {"local_srv", "로컬 서버"}, {"mode_connect", "연결"}, {"mode_local", "로컬"},
            {"host_mode", "호스트"}, {"solo", "솔로"}, {"coop", "협동"},
            {"copy", "복사"}, {"clear", "지우기"}, {"list", "목록"}, {"paste", "붙여넣기"}, {"add", "추가"},
            {"check", "확인"}, {"switch", "전환"}, {"sync", "동기화"}, {"rebuild", "재빌드"}, {"install", "설치"},
            {"allow", "허용"}, {"open", "열기"}, {"next", "다음"}, {"back", "뒤로"}, {"cont", "계속"}, {"recheck", "다시 확인"},
            {"refresh", "새로고침"}, {"new_btn", "+ 추가"}, {"backup", "백업"}, {"save", "저장"}, {"cancel", "취소"}, {"delete", "삭제"},
            {"edit", "편집"}, {"restore", "복원"},
            {"download", "다운로드"}, {"view", "보기"}, {"change", "변경"}, {"lang_title", "언어"}, {"lang_auto", "자동"},
            {"wiz_check_btn", "기기 확인"}, {"wiz_tagline", "Rust 기반 명조 3.8 서버 에뮬레이터"},
            {"wiz_checking", "기기 확인"}, {"wiz_setup", "설정"},
            {"setup_wiz", "초기 설정"}, {"setup_wiz_sub", "기기 확인, 권한 및 모드"},
            {"upd_avail", "업데이트 가능"}, {"checking_gh", "GitHub 확인 중..."}, {"up_to_date", "최신 버전"},
            {"tap_check", "확인을 눌러 업데이트 검색"}, {"app_ver", "앱 버전"}, {"updates", "업데이트"}, {"app_sec", "정보"},
            {"log_title", "서버 및 컴파일러 로그"}, {"output", "출력"}, {"priv", "권한"}, {"priv_stor", "권한 및 저장소"},
            {"local_compiler", "로컬 서버 및 컴파일러"}, {"log_lbl", "로그"},
            {"add_conn", "연결 추가"}, {"saved", "저장됨"}, {"no_saved_conns", "저장된 연결 없음"},
            {"granted", "허용됨"}, {"perm_needed", "권한 필요"}, {"players", "플레이어"}, {"backups", "백업"}
        });

        put("vi", new String[][]{
            {"tab_home", "Trang chủ"}, {"tab_conn", "Kết nối"}, {"tab_db", "CSDL"}, {"tab_sys", "Hệ thống"}, {"tab_about", "Giới thiệu"},
            {"ready", "Sẵn sàng"}, {"starting", "Đang chạy"}, {"connected", "Đã kết nối"}, {"failed", "Thất bại"}, {"working", "Đang xử lý"}, {"active", "Hoạt động"},
            {"start", "Bắt đầu"}, {"starting_btn", "Đang chạy…"}, {"stop", "Dừng"}, {"grant", "Cấp quyền"}, {"root", "Root"},
            {"access", "Quyền"}, {"mode", "Chế độ"}, {"state", "Trạng thái"}, {"uid", "UID"},
            {"connect_srv", "Máy chủ từ xa"}, {"local_srv", "Máy chủ nội bộ"}, {"mode_connect", "Kết nối"}, {"mode_local", "Nội bộ"},
            {"host_mode", "Chế độ Host"}, {"solo", "Đơn"}, {"coop", "Co-op"},
            {"copy", "Sao chép"}, {"clear", "Xóa"}, {"list", "Danh sách"}, {"paste", "Dán"}, {"add", "Thêm"},
            {"check", "Kiểm tra"}, {"switch", "Đổi"}, {"sync", "Đồng bộ"}, {"rebuild", "Biên dịch lại"}, {"install", "Cài đặt"},
            {"allow", "Cho phép"}, {"open", "Mở"}, {"next", "Tiếp"}, {"back", "Quay lại"}, {"cont", "Tiếp tục"}, {"recheck", "Kiểm tra lại"},
            {"refresh", "Làm mới"}, {"new_btn", "+ Mới"}, {"backup", "Sao lưu"}, {"save", "Lưu"}, {"cancel", "Hủy"}, {"delete", "Xóa"},
            {"edit", "Sửa"}, {"restore", "Khôi phục"},
            {"download", "Tải về"}, {"view", "Xem"}, {"change", "Đổi"}, {"lang_title", "Ngôn ngữ"}, {"lang_auto", "Tự động"},
            {"wiz_check_btn", "Kiểm tra máy"}, {"wiz_tagline", "Trình giả lập máy chủ Wuthering Waves 3.8 bằng Rust"},
            {"wiz_checking", "Kiểm tra máy"}, {"wiz_setup", "Thiết lập"},
            {"setup_wiz", "Thiết lập ban đầu"}, {"setup_wiz_sub", "Kiểm tra máy, quyền & chế độ"},
            {"upd_avail", "Có bản cập nhật"}, {"checking_gh", "Đang kiểm tra GitHub..."}, {"up_to_date", "Đã cập nhật"},
            {"tap_check", "Chạm Kiểm tra để tìm bản mới"}, {"app_ver", "Phiên bản"}, {"updates", "Cập nhật"}, {"app_sec", "Thông tin"},
            {"log_title", "Nhật ký Server & Trình biên dịch"}, {"priv", "Quyền"}, {"priv_stor", "Quyền & Bộ nhớ"},
            {"local_compiler", "Server nội bộ & Trình biên dịch"}, {"add_conn", "Thêm kết nối"}, {"saved", "Đã lưu"}
        });

        put("th", new String[][]{
            {"tab_home", "หน้าหลัก"}, {"tab_conn", "เชื่อมต่อ"}, {"tab_db", "ฐานข้อมูล"}, {"tab_sys", "ระบบ"}, {"tab_about", "เกี่ยวกับ"},
            {"ready", "พร้อม"}, {"starting", "กำลังเริ่ม"}, {"connected", "เชื่อมต่อแล้ว"}, {"failed", "ล้มเหลว"}, {"working", "กำลังทำงาน"}, {"active", "ทำงานอยู่"},
            {"start", "เริ่ม"}, {"starting_btn", "กำลังเริ่ม…"}, {"stop", "หยุด"}, {"grant", "อนุญาต"},
            {"access", "สิทธิ์"}, {"mode", "โหมด"}, {"state", "สถานะ"},
            {"copy", "คัดลอก"}, {"clear", "ล้าง"}, {"list", "รายการ"}, {"paste", "วาง"}, {"add", "เพิ่ม"},
            {"check", "ตรวจสอบ"}, {"switch", "สลับ"}, {"next", "ถัดไป"}, {"back", "ย้อนกลับ"}, {"cont", "ดำเนินการต่อ"},
            {"refresh", "รีเฟรช"}, {"new_btn", "+ ใหม่"}, {"backup", "สำรอง"}, {"save", "บันทึก"}, {"cancel", "ยกเลิก"}, {"delete", "ลบ"},
            {"download", "ดาวน์โหลด"}, {"view", "ดู"}, {"change", "เปลี่ยน"}, {"lang_title", "ภาษา"}, {"lang_auto", "อัตโนมัติ"},
            {"wiz_check_btn", "ตรวจสอบเครื่อง"}, {"setup_wiz", "ตั้งค่าเริ่มต้น"}, {"setup_wiz_sub", "ตรวจอุปกรณ์ สิทธิ์ และโหมด"},
            {"upd_avail", "มีอัปเดตใหม่"}, {"up_to_date", "เป็นเวอร์ชันล่าสุด"}
        });

        put("es", new String[][]{
            {"tab_home", "Inicio"}, {"tab_conn", "Conexión"}, {"tab_db", "Base de datos"}, {"tab_sys", "Sistema"}, {"tab_about", "Acerca de"},
            {"ready", "Listo"}, {"starting", "Iniciando"}, {"connected", "Conectado"}, {"failed", "Error"}, {"working", "Procesando"}, {"active", "Activo"},
            {"start", "Iniciar"}, {"starting_btn", "Iniciando…"}, {"stop", "Detener"}, {"grant", "Conceder"},
            {"access", "Acceso"}, {"mode", "Modo"}, {"state", "Estado"},
            {"copy", "Copiar"}, {"clear", "Limpiar"}, {"list", "Lista"}, {"paste", "Pegar"}, {"add", "Añadir"},
            {"check", "Comprobar"}, {"switch", "Cambiar"}, {"open", "Abrir"}, {"next", "Siguiente"}, {"back", "Atrás"}, {"cont", "Continuar"},
            {"refresh", "Actualizar"}, {"new_btn", "+ Nuevo"}, {"backup", "Respaldo"}, {"save", "Guardar"}, {"cancel", "Cancelar"}, {"delete", "Eliminar"},
            {"download", "Descargar"}, {"view", "Ver"}, {"change", "Cambiar"}, {"lang_title", "Idioma"}, {"lang_auto", "Auto"},
            {"wiz_check_btn", "Comprobar teléfono"}, {"setup_wiz", "Configuración inicial"}, {"setup_wiz_sub", "Dispositivo, acceso y modo"},
            {"upd_avail", "Actualización disponible"}, {"up_to_date", "Actualizado"}
        });

        put("pt", new String[][]{
            {"tab_home", "Início"}, {"tab_conn", "Conexão"}, {"tab_db", "Banco"}, {"tab_sys", "Sistema"}, {"tab_about", "Sobre"},
            {"ready", "Pronto"}, {"starting", "Iniciando"}, {"connected", "Conectado"}, {"failed", "Falhou"}, {"working", "Processando"}, {"active", "Ativo"},
            {"start", "Iniciar"}, {"starting_btn", "Iniciando…"}, {"stop", "Parar"}, {"grant", "Permitir"},
            {"access", "Acesso"}, {"mode", "Modo"}, {"state", "Estado"},
            {"copy", "Copiar"}, {"clear", "Limpar"}, {"list", "Lista"}, {"paste", "Colar"}, {"add", "Adicionar"},
            {"check", "Verificar"}, {"switch", "Trocar"}, {"open", "Abrir"}, {"next", "Avançar"}, {"back", "Voltar"}, {"cont", "Continuar"},
            {"refresh", "Atualizar"}, {"new_btn", "+ Novo"}, {"backup", "Backup"}, {"save", "Salvar"}, {"cancel", "Cancelar"}, {"delete", "Excluir"},
            {"download", "Baixar"}, {"view", "Ver"}, {"change", "Alterar"}, {"lang_title", "Idioma"}, {"lang_auto", "Auto"},
            {"wiz_check_btn", "Verificar celular"}, {"setup_wiz", "Configuração inicial"}, {"setup_wiz_sub", "Dispositivo, acesso e modo"},
            {"upd_avail", "Atualização disponível"}, {"up_to_date", "Atualizado"}
        });

        put("fr", new String[][]{
            {"tab_home", "Accueil"}, {"tab_conn", "Connexion"}, {"tab_db", "Base"}, {"tab_sys", "Système"}, {"tab_about", "À propos"},
            {"ready", "Prêt"}, {"starting", "Démarrage"}, {"connected", "Connecté"}, {"failed", "Échec"}, {"working", "En cours"}, {"active", "Actif"},
            {"start", "Démarrer"}, {"starting_btn", "Démarrage…"}, {"stop", "Arrêter"}, {"grant", "Autoriser"},
            {"access", "Accès"}, {"mode", "Mode"}, {"state", "État"},
            {"copy", "Copier"}, {"clear", "Effacer"}, {"list", "Liste"}, {"paste", "Coller"}, {"add", "Ajouter"},
            {"check", "Vérifier"}, {"switch", "Changer"}, {"open", "Ouvrir"}, {"next", "Suivant"}, {"back", "Retour"}, {"cont", "Continuer"},
            {"refresh", "Actualiser"}, {"new_btn", "+ Nouveau"}, {"backup", "Sauvegarder"}, {"save", "Enregistrer"}, {"cancel", "Annuler"}, {"delete", "Supprimer"},
            {"download", "Télécharger"}, {"view", "Voir"}, {"change", "Changer"}, {"lang_title", "Langue"}, {"lang_auto", "Auto"},
            {"wiz_check_btn", "Vérifier le téléphone"}, {"setup_wiz", "Configuration initiale"}, {"setup_wiz_sub", "Appareil, accès et mode"},
            {"upd_avail", "Mise à jour disponible"}, {"up_to_date", "À jour"}
        });

        put("de", new String[][]{
            {"tab_home", "Start"}, {"tab_conn", "Verbindung"}, {"tab_db", "Datenbank"}, {"tab_sys", "System"}, {"tab_about", "Über"},
            {"ready", "Bereit"}, {"starting", "Startet"}, {"connected", "Verbunden"}, {"failed", "Fehler"}, {"working", "Läuft"}, {"active", "Aktiv"},
            {"start", "Starten"}, {"starting_btn", "Startet…"}, {"stop", "Stopp"}, {"grant", "Erlauben"},
            {"access", "Zugriff"}, {"mode", "Modus"}, {"state", "Status"},
            {"copy", "Kopieren"}, {"clear", "Leeren"}, {"list", "Liste"}, {"paste", "Einfügen"}, {"add", "Hinzufügen"},
            {"check", "Prüfen"}, {"switch", "Wechseln"}, {"open", "Öffnen"}, {"next", "Weiter"}, {"back", "Zurück"}, {"cont", "Fortfahren"},
            {"refresh", "Aktualisieren"}, {"new_btn", "+ Neu"}, {"backup", "Sichern"}, {"save", "Speichern"}, {"cancel", "Abbrechen"}, {"delete", "Löschen"},
            {"download", "Herunterladen"}, {"view", "Ansehen"}, {"change", "Ändern"}, {"lang_title", "Sprache"}, {"lang_auto", "Auto"},
            {"wiz_check_btn", "Gerät prüfen"}, {"setup_wiz", "Ersteinrichtung"}, {"setup_wiz_sub", "Geräteprüfung, Zugriff & Modus"},
            {"upd_avail", "Update verfügbar"}, {"up_to_date", "Aktuell"}
        });

        put("ru", new String[][]{
            {"tab_home", "Главная"}, {"tab_conn", "Подключение"}, {"tab_db", "База"}, {"tab_sys", "Система"}, {"tab_about", "О программе"},
            {"ready", "Готово"}, {"starting", "Запуск"}, {"connected", "Подключено"}, {"failed", "Ошибка"}, {"working", "Выполняется"}, {"active", "Активно"},
            {"start", "Старт"}, {"starting_btn", "Запуск…"}, {"stop", "Стоп"}, {"grant", "Выдать"},
            {"access", "Доступ"}, {"mode", "Режим"}, {"state", "Статус"},
            {"copy", "Копировать"}, {"clear", "Очистить"}, {"list", "Список"}, {"paste", "Вставить"}, {"add", "Добавить"},
            {"check", "Проверить"}, {"switch", "Сменить"}, {"open", "Открыть"}, {"next", "Далее"}, {"back", "Назад"}, {"cont", "Продолжить"},
            {"refresh", "Обновить"}, {"new_btn", "+ Создать"}, {"backup", "Копия"}, {"save", "Сохранить"}, {"cancel", "Отмена"}, {"delete", "Удалить"},
            {"download", "Скачать"}, {"view", "Открыть"}, {"change", "Изменить"}, {"lang_title", "Язык"}, {"lang_auto", "Авто"},
            {"wiz_check_btn", "Проверить телефон"}, {"setup_wiz", "Начальная настройка"}, {"setup_wiz_sub", "Устройство, доступ и режим"},
            {"upd_avail", "Доступно обновление"}, {"up_to_date", "Актуально"}
        });

        put("ar", new String[][]{
            {"tab_home", "الرئيسية"}, {"tab_conn", "الاتصال"}, {"tab_db", "القاعدة"}, {"tab_sys", "النظام"}, {"tab_about", "حول"},
            {"ready", "جاهز"}, {"starting", "جارٍ البدء"}, {"connected", "متصل"}, {"failed", "فشل"}, {"working", "قيد العمل"}, {"active", "نشط"},
            {"start", "بدء"}, {"starting_btn", "جارٍ البدء…"}, {"stop", "إيقاف"}, {"grant", "سماح"},
            {"access", "الوصول"}, {"mode", "الوضع"}, {"state", "الحالة"},
            {"copy", "نسخ"}, {"clear", "مسح"}, {"list", "القائمة"}, {"paste", "لصق"}, {"add", "إضافة"},
            {"check", "فحص"}, {"switch", "تبديل"}, {"open", "فتح"}, {"next", "التالي"}, {"back", "رجوع"}, {"cont", "متابعة"},
            {"refresh", "تحديث"}, {"new_btn", "+ جديد"}, {"backup", "نسخ احتياطي"}, {"save", "حفظ"}, {"cancel", "إلغاء"}, {"delete", "حذف"},
            {"download", "تنزيل"}, {"view", "عرض"}, {"change", "تغيير"}, {"lang_title", "اللغة"}, {"lang_auto", "تلقائي"},
            {"wiz_check_btn", "فحص الهاتف"}, {"setup_wiz", "الإعداد الأولي"}, {"setup_wiz_sub", "فحص الجهاز والصلاحيات والوضع"},
            {"upd_avail", "يتوفر تحديث"}, {"up_to_date", "محدّث"}
        });

        put("tr", new String[][]{
            {"tab_home", "Ana Sayfa"}, {"tab_conn", "Bağlantı"}, {"tab_db", "Veritabanı"}, {"tab_sys", "Sistem"}, {"tab_about", "Hakkında"},
            {"ready", "Hazır"}, {"starting", "Başlatılıyor"}, {"connected", "Bağlı"}, {"failed", "Hata"}, {"working", "İşleniyor"}, {"active", "Etkin"},
            {"start", "Başlat"}, {"starting_btn", "Başlatılıyor…"}, {"stop", "Durdur"}, {"grant", "İzin Ver"},
            {"access", "Erişim"}, {"mode", "Mod"}, {"state", "Durum"},
            {"copy", "Kopyala"}, {"clear", "Temizle"}, {"list", "Liste"}, {"paste", "Yapıştır"}, {"add", "Ekle"},
            {"check", "Kontrol et"}, {"switch", "Değiştir"}, {"open", "Aç"}, {"next", "İleri"}, {"back", "Geri"}, {"cont", "Devam"},
            {"refresh", "Yenile"}, {"new_btn", "+ Yeni"}, {"backup", "Yedekle"}, {"save", "Kaydet"}, {"cancel", "İptal"}, {"delete", "Sil"},
            {"download", "İndir"}, {"view", "Görüntüle"}, {"change", "Değiştir"}, {"lang_title", "Dil"}, {"lang_auto", "Otomatik"},
            {"wiz_check_btn", "Telefonu kontrol et"}, {"setup_wiz", "İlk kurulum"}, {"setup_wiz_sub", "Cihaz kontrolü, erişim ve mod"},
            {"upd_avail", "Güncelleme mevcut"}, {"up_to_date", "Güncel"}
        });

        put("fil", new String[][]{
            {"tab_home", "Home"}, {"tab_conn", "Koneksyon"}, {"tab_db", "Database"}, {"tab_sys", "System"}, {"tab_about", "Tungkol"},
            {"ready", "Handa"}, {"starting", "Sinisimulan"}, {"connected", "Nakakonekta"}, {"failed", "Nabigo"}, {"working", "Gumagana"}, {"active", "Aktibo"},
            {"start", "Simulan"}, {"starting_btn", "Sinisimulan…"}, {"stop", "Ihinto"}, {"grant", "Payagan"},
            {"access", "Access"}, {"mode", "Mode"}, {"state", "Status"},
            {"copy", "Kopyahin"}, {"clear", "Burahin"}, {"list", "Listahan"}, {"paste", "I-paste"}, {"add", "Idagdag"},
            {"check", "Suriin"}, {"switch", "Palitan"}, {"open", "Buksan"}, {"next", "Susunod"}, {"back", "Balik"}, {"cont", "Magpatuloy"},
            {"refresh", "I-refresh"}, {"new_btn", "+ Bago"}, {"backup", "Backup"}, {"save", "I-save"}, {"cancel", "Kanselahin"}, {"delete", "Tanggalin"},
            {"download", "I-download"}, {"view", "Tingnan"}, {"change", "Palitan"}, {"lang_title", "Wika"}, {"lang_auto", "Auto"},
            {"wiz_check_btn", "Suriin ang telepono"}, {"setup_wiz", "Unang setup"}, {"setup_wiz_sub", "Pagsusuri, access at mode"},
            {"upd_avail", "May bagong update"}, {"up_to_date", "Naka-update"}
        });
    }

    private static void put(String code, String[][] pairs) {
        Map<String, String> m = new HashMap<>();
        for (String[] p : pairs) m.put(p[0], p[1]);
        TABLE.put(code, m);
    }

    private I18n() {}

    public static String getPrefCode(Context ctx) {
        if (ctx == null) return "auto";
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANG, "auto");
    }

    public static void setPrefCode(Context ctx, String code) {
        if (ctx == null) return;
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LANG, code).apply();
    }

    public static String resolveEffectiveCode(Context ctx) {
        String pref = getPrefCode(ctx);
        if (pref != null && !"auto".equals(pref) && TABLE.containsKey(pref)) {
            return pref;
        }
        Locale loc = ctx != null ? ctx.getResources().getConfiguration().getLocales().get(0) : Locale.getDefault();
        if (loc == null) loc = Locale.getDefault();
        String lang = loc.getLanguage();
        String country = loc.getCountry();
        if ("in".equals(lang) || "id".equals(lang)) return "id";
        if ("tl".equals(lang) || "fil".equals(lang)) return "fil";
        if ("zh".equals(lang)) {
            if ("TW".equalsIgnoreCase(country) || "HK".equalsIgnoreCase(country) || "MO".equalsIgnoreCase(country)) {
                return "zh-TW";
            }
            return "zh";
        }
        if (TABLE.containsKey(lang)) return lang;
        return "en";
    }

    public static String t(Context ctx, String key) {
        String code = resolveEffectiveCode(ctx);
        Map<String, String> m = TABLE.get(code);
        if (m != null) {
            String val = m.get(key);
            if (val != null) return val;
        }
        Map<String, String> en = TABLE.get("en");
        if (en != null && en.containsKey(key)) return en.get(key);
        return key;
    }

    public static String currentLabel(Context ctx) {
        String pref = getPrefCode(ctx);
        String eff = resolveEffectiveCode(ctx);
        String effName = displayNameFor(eff);
        if ("auto".equals(pref)) {
            return t(ctx, "lang_auto") + " (" + effName + ")";
        }
        return displayNameFor(pref);
    }

    private static String displayNameFor(String code) {
        for (int i = 0; i < LANG_CODES.length; i++) {
            if (LANG_CODES[i].equals(code)) return LANG_NAMES[i];
        }
        return code;
    }

    public static void showPicker(Context ctx, final Runnable onChanged) {
        String cur = getPrefCode(ctx);
        int sel = 0;
        for (int i = 0; i < LANG_CODES.length; i++) {
            if (LANG_CODES[i].equals(cur)) {
                sel = i;
                break;
            }
        }
        new AlertDialog.Builder(ctx)
            .setTitle(t(ctx, "lang_title"))
            .setSingleChoiceItems(LANG_NAMES, sel, (dlg, which) -> {
                setPrefCode(ctx, LANG_CODES[which]);
                dlg.dismiss();
                if (onChanged != null) onChanged.run();
            })
            .setNegativeButton(t(ctx, "cancel"), null)
            .show();
    }
}
