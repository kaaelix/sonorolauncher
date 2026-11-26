use rusqlite::{params, Connection};
use serde_json::{json, Value};
use std::fs;
use std::path::Path;
use std::time::{SystemTime, UNIX_EPOCH};

fn unix_now() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs() as i64)
        .unwrap_or(0)
}

fn open_and_ensure(path: &Path) -> Result<Connection, String> {
    if let Some(parent) = path.parent() {
        if !parent.as_os_str().is_empty() {
            fs::create_dir_all(parent)
                .map_err(|e| format!("Failed creating DB dir {}: {e}", parent.display()))?;
        }
    }
    let conn = Connection::open(path)
        .map_err(|e| format!("Failed opening {}: {e}", path.display()))?;
    let _ = conn.execute_batch("PRAGMA busy_timeout=5000; PRAGMA synchronous=NORMAL;");
    if conn.execute_batch("PRAGMA journal_mode=WAL;").is_err() {
        let _ = conn.execute_batch("PRAGMA journal_mode=TRUNCATE;");
    }
    conn.execute_batch(
        "
CREATE TABLE IF NOT EXISTS state_files (
    path TEXT PRIMARY KEY,
    content TEXT NOT NULL,
    updated_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS players (
    player_id INTEGER PRIMARY KEY,
    name TEXT NOT NULL DEFAULT '',
    level INTEGER NOT NULL DEFAULT 1,
    exp INTEGER NOT NULL DEFAULT 0,
    coin INTEGER NOT NULL DEFAULT 0,
    rare_coin INTEGER NOT NULL DEFAULT 0,
    cash_coin INTEGER NOT NULL DEFAULT 0,
    sex INTEGER NOT NULL DEFAULT 1,
    world_level INTEGER NOT NULL DEFAULT 1,
    updated_at INTEGER NOT NULL DEFAULT 0
);
",
    )
    .map_err(|e| format!("Failed ensuring schema: {e}"))?;
    Ok(conn)
}

pub fn list_players(db_path: &Path) -> Value {
    let conn = match open_and_ensure(db_path) {
        Ok(c) => c,
        Err(e) => return json!({ "ok": false, "error": e, "players": [] }),
    };
    let mut stmt = match conn.prepare(
        "SELECT player_id, name, level, exp, coin, rare_coin, cash_coin, sex, world_level, updated_at
         FROM players ORDER BY player_id ASC",
    ) {
        Ok(s) => s,
        Err(e) => return json!({ "ok": false, "error": e.to_string(), "players": [] }),
    };
    let rows = match stmt.query_map([], |r| {
        Ok(json!({
            "playerId": r.get::<_, i64>(0)?,
            "name": r.get::<_, String>(1)?,
            "level": r.get::<_, i64>(2)?,
            "exp": r.get::<_, i64>(3)?,
            "coin": r.get::<_, i64>(4)?,
            "rareCoin": r.get::<_, i64>(5)?,
            "cashCoin": r.get::<_, i64>(6)?,
            "sex": r.get::<_, i64>(7)?,
            "worldLevel": r.get::<_, i64>(8)?,
            "updatedAt": r.get::<_, i64>(9)?
        }))
    }) {
        Ok(it) => it,
        Err(e) => return json!({ "ok": false, "error": e.to_string(), "players": [] }),
    };

    let mut players = Vec::new();
    for item in rows {
        if let Ok(v) = item {
            players.push(v);
        }
    }
    let file_size = fs::metadata(db_path).map(|m| m.len()).unwrap_or(0);
    json!({
        "ok": true,
        "path": db_path.to_string_lossy(),
        "size": file_size,
        "count": players.len(),
        "players": players
    })
}

pub fn upsert_player(db_path: &Path, payload_json: &str) -> Result<i64, String> {
    let v: Value = serde_json::from_str(payload_json).map_err(|e| format!("JSON error: {e}"))?;
    let conn = open_and_ensure(db_path)?;
    let mut pid = v.get("playerId").and_then(|x| x.as_i64()).unwrap_or(0);
    if pid <= 0 {
        let max_id: i64 = conn
            .query_row("SELECT COALESCE(MAX(player_id), 597938509) FROM players", [], |r| r.get(0))
            .unwrap_or(597938509);
        pid = max_id + 1;
    }
    let name = v.get("name").and_then(|x| x.as_str()).unwrap_or("Rover").trim();
    let name = if name.is_empty() { "Rover" } else { name };
    let level = v.get("level").and_then(|x| x.as_i64()).unwrap_or(1).clamp(1, 90);
    let exp = v.get("exp").and_then(|x| x.as_i64()).unwrap_or(0).max(0);
    let coin = v.get("coin").and_then(|x| x.as_i64()).unwrap_or(0).max(0);
    let rare_coin = v.get("rareCoin").and_then(|x| x.as_i64()).unwrap_or(0).max(0);
    let cash_coin = v.get("cashCoin").and_then(|x| x.as_i64()).unwrap_or(0).max(0);
    let sex = v.get("sex").and_then(|x| x.as_i64()).unwrap_or(1).clamp(0, 2);
    let world_level = v
        .get("worldLevel")
        .and_then(|x| x.as_i64())
        .unwrap_or(1)
        .clamp(1, 10);
    let now = unix_now();

    conn.execute(
        "INSERT INTO players (player_id, name, level, exp, coin, rare_coin, cash_coin, sex, world_level, updated_at)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10)
         ON CONFLICT(player_id) DO UPDATE SET
            name=excluded.name,
            level=excluded.level,
            exp=excluded.exp,
            coin=excluded.coin,
            rare_coin=excluded.rare_coin,
            cash_coin=excluded.cash_coin,
            sex=excluded.sex,
            world_level=excluded.world_level,
            updated_at=excluded.updated_at",
        params![pid, name, level, exp, coin, rare_coin, cash_coin, sex, world_level, now],
    )
    .map_err(|e| format!("Failed saving player: {e}"))?;
    let _ = conn.execute_batch("PRAGMA wal_checkpoint(TRUNCATE);");
    Ok(pid)
}

pub fn delete_player(db_path: &Path, player_id: i64) -> Result<(), String> {
    let conn = open_and_ensure(db_path)?;
    conn.execute("DELETE FROM players WHERE player_id = ?1", params![player_id])
        .map_err(|e| format!("Delete error: {e}"))?;
    for tbl in [
        "player_battlepass",
        "player_activity_claims",
        "player_liveness",
        "player_achievements",
        "player_shop_bought",
        "player_kv",
    ] {
        let sql = format!("DELETE FROM {tbl} WHERE player_id = ?1");
        let _ = conn.execute(&sql, params![player_id]);
    }
    let _ = conn.execute_batch("PRAGMA wal_checkpoint(TRUNCATE);");
    Ok(())
}

pub fn checkpoint_and_copy(src: &Path, dst: &Path) -> Result<(), String> {
    let src_conn = open_and_ensure(src)?;
    let _ = src_conn.execute_batch("PRAGMA wal_checkpoint(TRUNCATE);");
    drop(src_conn);
    if let Some(parent) = dst.parent() {
        if !parent.as_os_str().is_empty() {
            fs::create_dir_all(parent)
                .map_err(|e| format!("Failed creating dest dir {}: {e}", parent.display()))?;
        }
    }
    let tmp = dst.with_extension("tmp");
    fs::copy(src, &tmp).map_err(|e| format!("Copy failed: {e}"))?;
    fs::rename(&tmp, dst).map_err(|e| format!("Rename failed: {e}"))?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::env;

    #[test]
    fn player_crud_works() {
        let p = env::temp_dir().join("sonoro_player_test.db");
        let _ = fs::remove_file(&p);
        let pid = upsert_player(
            &p,
            r#"{"playerId":123456,"name":"RoverTest","level":45,"exp":100,"coin":50000,"rareCoin":1600,"cashCoin":300,"sex":2,"worldLevel":5}"#,
        )
        .unwrap();
        assert_eq!(pid, 123456);
        let list = list_players(&p);
        assert_eq!(list["ok"], true);
        assert_eq!(list["count"], 1);
        assert_eq!(list["players"][0]["name"], "RoverTest");
        assert_eq!(list["players"][0]["sex"], 2);
        delete_player(&p, 123456).unwrap();
        let after = list_players(&p);
        assert_eq!(after["count"], 0);
        let _ = fs::remove_file(&p);
    }
}
