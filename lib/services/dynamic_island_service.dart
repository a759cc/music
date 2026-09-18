/// Dynamic island service disabled/removed
class DynamicIslandService {
  static void init() {}
  static Future<bool> checkPermission() async => true;
  static Future<void> requestPermission() async {}
  static Future<void> ensureOverlayPermission() async {}
  static Future<void> showIsland({
    bool isPlaying = true,
    String title = '',
    String artist = '',
    String artUri = '',
  }) async {}
  static Future<void> hideIsland() async {}
  static Future<void> updateState({
    bool isPlaying = false,
    int positionMs = 0,
    int durationMs = 0,
    String title = '',
    String artist = '',
    String artUri = '',
  }) async {}
}
