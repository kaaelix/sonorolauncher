use std::net::{TcpStream, ToSocketAddrs};
use std::time::Duration;

pub fn check(ip: &str, port: u16) -> Result<(), String> {
    let addr = format!("{ip}:{port}")
        .to_socket_addrs()
        .map_err(|e| e.to_string())?
        .next()
        .ok_or("invalid address")?;
    TcpStream::connect_timeout(&addr, Duration::from_secs(4))
        .map(|_| ())
        .map_err(|e| e.to_string())
}
