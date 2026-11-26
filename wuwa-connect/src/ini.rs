pub enum Change {
    Ok,
    Added,
    Changed(String),
}

/// Set `key=val` inside `[section]`. Creates the section/key when missing.
pub fn set_key(text: &str, section: &str, key: &str, val: &str) -> (String, Change) {
    let mut lines: Vec<String> = text.lines().map(String::from).collect();
    let header = format!("[{section}]");
    let join = |l: &Vec<String>| l.join("\n") + "\n";

    let start = lines.iter().position(|l| l.trim().eq_ignore_ascii_case(&header));
    let Some(s) = start else {
        if lines.last().map_or(false, |l| !l.is_empty()) {
            lines.push(String::new());
        }
        lines.push(header);
        lines.push(format!("{key}={val}"));
        return (join(&lines), Change::Added);
    };

    let end = lines[s + 1..]
        .iter()
        .position(|l| l.trim_start().starts_with('['))
        .map(|i| s + 1 + i)
        .unwrap_or(lines.len());

    for i in s + 1..end {
        let old = lines[i]
            .split_once('=')
            .filter(|(k, _)| k.trim().eq_ignore_ascii_case(key))
            .map(|(_, v)| v.trim().to_string());
        if let Some(old) = old {
            if old.eq_ignore_ascii_case(val) {
                return (text.to_string(), Change::Ok);
            }
            lines[i] = format!("{key}={val}");
            return (join(&lines), Change::Changed(old));
        }
    }

    lines.insert(end, format!("{key}={val}"));
    (join(&lines), Change::Added)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn creates_missing_section_and_key() {
        let (out, ch) = set_key("", "HTTP", "bVerifyPeer", "False");
        assert!(matches!(ch, Change::Added));
        assert_eq!(out, "[HTTP]\nbVerifyPeer=False\n");
    }

    #[test]
    fn updates_existing_key_and_preserves_matching() {
        let input = "[HTTP]\nbVerifyPeer=True\n";
        let (out, ch) = set_key(input, "HTTP", "bVerifyPeer", "False");
        assert!(matches!(ch, Change::Changed(ref old) if old == "True"));
        assert_eq!(out, "[HTTP]\nbVerifyPeer=False\n");

        let (out2, ch2) = set_key(&out, "HTTP", "bVerifyPeer", "False");
        assert!(matches!(ch2, Change::Ok));
        assert_eq!(out2, out);
    }
}
