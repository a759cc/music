import 'package:flutter/services.dart';
import 'package:get/get.dart';
import '/ui/player/player_controller.dart';

class DynamicIslandService {
  static const MethodChannel _channel =
      MethodChannel('com.anandnet.harmonymusic/island');

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

  static Future<void> showIsland({bool isPlaying = true}) async {
    try {
      await _channel.invokeMethod('showIsland', {'isPlaying': isPlaying});
    } catch (_) {}
  }

  static Future<void> hideIsland() async {
    try {
      await _channel.invokeMethod('hideIsland');
    } catch (_) {}
  }

  static Future<void> updateState({bool isPlaying = false}) async {
    try {
      await _channel.invokeMethod('updateState', {'isPlaying': isPlaying});
    } catch (_) {}
  }
}
