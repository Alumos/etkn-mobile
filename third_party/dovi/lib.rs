//! One C entry point: rewrite a Dolby Vision profile 7 RPU as profile 8.1.
//!
//! Profile 7 (dual layer, UHD Blu-ray) has no phone decoder. Profile 8.1 is the base layer plus
//! the same dynamic metadata, which Android's Dolby Vision decoders play. The enhancement layer
//! is dropped by the caller; this only converts the metadata (dovi_tool's `convert` mode 2).

use dolby_vision::rpu::dovi_rpu::DoviRpu;
use dolby_vision::rpu::ConversionMode;

/// `input`: one HEVC UNSPEC62 NAL unit starting at its `0x7C01` header, emulation-prevention
/// bytes included. Writes the converted NAL (same framing) to `out` and returns its length;
/// -1 bad arguments, -2 unparsable or unconvertible RPU, -3 `out` too small.
///
/// # Safety
/// `input` must be valid for `length` bytes and `out` for `capacity` bytes.
#[no_mangle]
pub unsafe extern "C" fn etkn_dovi_to_p81(input: *const u8, length: usize, out: *mut u8, capacity: usize) -> i64 {
    if input.is_null() || out.is_null() || length < 3 {
        return -1;
    }
    let data = std::slice::from_raw_parts(input, length);
    let converted = std::panic::catch_unwind(|| -> Option<Vec<u8>> {
        let mut rpu = DoviRpu::parse_unspec62_nalu(data).ok()?;
        rpu.convert_with_mode(ConversionMode::To81).ok()?;
        rpu.write_hevc_unspec62_nalu().ok()
    });
    match converted {
        Ok(Some(bytes)) if bytes.len() <= capacity => {
            std::ptr::copy_nonoverlapping(bytes.as_ptr(), out, bytes.len());
            bytes.len() as i64
        }
        Ok(Some(_)) => -3,
        _ => -2,
    }
}
