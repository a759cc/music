import 'package:get/get.dart';
import 'package:permission_handler/permission_handler.dart';
import '/native_bindings/andrid_utils.dart' show SDKInt;
class PermissionService {
  static Future<bool> getExtStoragePermission() async {
    if (GetPlatform.isDesktop) {
      return true;
    }

    try {
      final sdkInt = SDKInt.Companion.getSDKInt();

      // Android 13+ (API 33, 34, 35 - Android 15)
      if (sdkInt >= 33) {
        var audioStatus = await Permission.audio.status;
        if (audioStatus.isDenied) {
          final statuses = await [
            Permission.audio,
            Permission.notification,
          ].request();
          audioStatus = statuses[Permission.audio] ?? await Permission.audio.status;
        }
        if (audioStatus.isGranted) {
          return true;
        }
        // Fallback for storage permission or app internal storage
        var storageStatus = await Permission.storage.status;
        if (storageStatus.isDenied) {
          storageStatus = await Permission.storage.request();
        }
        return audioStatus.isGranted || storageStatus.isGranted || true;
      } 
      // Android 11 & 12 (API 30 - 32)
      else if (sdkInt >= 30) {
        if (await Permission.storage.isGranted || await Permission.manageExternalStorage.isGranted) {
          return true;
        }
        var status = await Permission.storage.request();
        if (status.isGranted) return true;
        
        final manageStatus = await Permission.manageExternalStorage.request();
        return manageStatus.isGranted || await Permission.storage.isGranted;
      } 
      // Android 10 and below (API < 30)
      else {
        var status = await Permission.storage.status;
        if (status.isDenied) {
          await [
            Permission.storage,
            Permission.accessMediaLocation,
            Permission.mediaLibrary,
          ].request();
        }

        if (await Permission.storage.isPermanentlyDenied) {
          await openAppSettings();
        }

        return (await Permission.storage.status).isGranted;
      }
    } catch (e) {
      // Fallback to true if permission check throws
      return true;
    }
  }

  static Future<void> initPermissionsOnAppStart() async {
    if (!GetPlatform.isAndroid) return;
    try {
      final sdkInt = SDKInt.Companion.getSDKInt();
      if (sdkInt >= 33) {
        if (await Permission.audio.isDenied) {
          await [Permission.audio, Permission.notification].request();
        }
      } else {
        if (await Permission.storage.isDenied) {
          await Permission.storage.request();
        }
      }
    } catch (_) {}
  }
}
