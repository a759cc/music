import 'package:audio_service/audio_service.dart';
import 'package:flutter/services.dart';
import 'package:get/get.dart';
import '/ui/player/player_controller.dart';

class DynamicIslandService {
  static const MethodChannel _channel =
      MethodChannel('com.anandnet.harmonymusic/island');

  static bool _initialized = false;

  /// Initialize reverse call handler (native overlay → Flutter media control)
  static void init() {
    if (_initialized) return;
    _initialized = true;

    _channel.setMethodCallHandler((call) async {
      if (!Get.isRegistered<PlayerController>()) return;
      final pc = Get.find<PlayerController>();

      switch (call.method) {
        case 'mediaControl':
          final action = call.arguments['action'] as String?;
          if (action == 'playPause') {
            pc.playPause();
          } else if (action == 'prev') {
            pc.prev();
          } else if (action == 'next') {
            pc.next();
          }
          break;
        case 'seekTo':
          final posMs = call.arguments['positionMs'] as int? ?? 0;
          pc.seek(Duration(milliseconds: posMs));
          break;
      }
    });
  }

  static Future<bool> checkPermission() async {
    try {
      final bool res = await _channel.invokeMethod('checkPermission');
      return res;
    } catch (_) {
      return false;
    }
  }

  static Future<void> requestPermission() async {
    try {
      await _channel.invokeMethod('requestPermission');
    } catch (_) {}
  }

  /// Auto-request overlay permission if not granted
  static Future<void> ensureOverlayPermission() async {
    try {
      final granted = await checkPermission();
      if (!granted) {
        await _channel.invokeMethod('requestPermission');
      }
    } catch (_) {}
  }

  static Future<void> showIsland({
    bool isPlaying = true,
    String title = '',
    String artist = '',
  }) async {
    try {
      await _channel.invokeMethod('showIsland', {
        'isPlaying': isPlaying,
        'title': title,
        'artist': artist,
      });
    } catch (_) {}
  }

  static Future<void> hideIsland() async {
    try {
      await _channel.invokeMethod('hideIsland');
    } catch (_) {}
  }

  static Future<void> updateState({
    bool isPlaying = false,
    int positionMs = 0,
    int durationMs = 0,
    String title = '',
    String artist = '',
  }) async {
    try {
      await _channel.invokeMethod('updateState', {
        'isPlaying': isPlaying,
        'positionMs': positionMs,
        'durationMs': durationMs,
        'title': title,
        'artist': artist,
      });
    } catch (_) {}
  }
}
