import 'dart:io';
import 'package:audiotags/audiotags.dart';
import 'package:audio_service/audio_service.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:hive/hive.dart';
import 'package:path/path.dart' as p;

import '/models/media_Item_builder.dart';
import '/ui/screens/Library/library_controller.dart';
import '/ui/screens/Settings/settings_screen_controller.dart';
import '/ui/widgets/snackbar.dart';
import 'permission_service.dart';

class LocalMusicScanner {
  static final List<String> supportedExtensions = [
    '.mp3',
    '.flac',
    '.wav',
    '.m4a',
    '.ogg',
    '.aac',
    '.opus',
    '.wma'
  ];

  /// Pick and scan a folder chosen by the user
  static Future<int> scanDirectoryPicker() async {
    final granted = await PermissionService.getExtStoragePermission();
    if (!granted && !GetPlatform.isDesktop) {
      _showMsg("未授予存储访问权限，无法扫描本地音乐");
      return 0;
    }

    try {
      final selectedPath = await FilePicker.platform.getDirectoryPath(
        dialogTitle: "请选择存放音乐的文件夹",
      );
      if (selectedPath == null || selectedPath.isEmpty) return 0;

      return await scanPath(selectedPath);
    } catch (e) {
      _showMsg("选择文件夹失败: $e");
      return 0;
    }
  }

  /// Pick one or multiple audio files directly
  static Future<int> pickAudioFiles() async {
    final granted = await PermissionService.getExtStoragePermission();
    if (!granted && !GetPlatform.isDesktop) {
      _showMsg("未授予存储访问权限");
      return 0;
    }

    try {
      final result = await FilePicker.platform.pickFiles(
        type: FileType.custom,
        allowedExtensions: supportedExtensions.map((e) => e.replaceAll('.', '')).toList(),
        allowMultiple: true,
        dialogTitle: "选择要导入的本地音频",
      );

      if (result == null || result.files.isEmpty) return 0;

      final files = result.paths
          .whereType<String>()
          .map((path) => File(path))
          .where((f) => f.existsSync())
          .toList();

      return await importAudioFiles(files);
    } catch (e) {
      _showMsg("选择文件失败: $e");
      return 0;
    }
  }

  /// Quick scan common system music and download directories
  static Future<int> quickScanCommonDirectories() async {
    final granted = await PermissionService.getExtStoragePermission();
    if (!granted && !GetPlatform.isDesktop) {
      _showMsg("未授予存储访问权限");
      return 0;
    }

    final List<String> candidatePaths = [];

    if (GetPlatform.isAndroid) {
      candidatePaths.addAll([
        "/storage/emulated/0/Music",
        "/storage/emulated/0/Download",
        "/storage/emulated/0/qqmusic/song",
        "/storage/emulated/0/NetEase/CloudMusic",
        "/storage/emulated/0/KuGou/Music",
      ]);
    } else if (GetPlatform.isWindows) {
      final userProfile = Platform.environment['USERPROFILE'] ?? '';
      if (userProfile.isNotEmpty) {
        candidatePaths.add("$userProfile\\Music");
        candidatePaths.add("$userProfile\\Downloads");
      }
    }

    int totalAdded = 0;
    for (final dirPath in candidatePaths) {
      final dir = Directory(dirPath);
      if (dir.existsSync()) {
        totalAdded += await scanPath(dirPath, showFeedback: false);
      }
    }

    _showMsg("常用目录扫描完成，共新增导入 $totalAdded 首本地歌曲");
    return totalAdded;
  }

  /// Recursively scan a directory path for supported audio files
  static Future<int> scanPath(String path, {bool showFeedback = true}) async {
    final rootDir = Directory(path);
    if (!rootDir.existsSync()) {
      if (showFeedback) _showMsg("指定路径不存在: $path");
      return 0;
    }

    List<File> audioFiles = [];
    try {
      final entities = rootDir.listSync(recursive: true, followLinks: false);
      for (final entity in entities) {
        if (entity is File) {
          final ext = p.extension(entity.path).toLowerCase();
          if (supportedExtensions.contains(ext)) {
            audioFiles.add(entity);
          }
        }
      }
    } catch (e) {
      if (showFeedback) _showMsg("遍历目录异常: $e");
      return 0;
    }

    if (audioFiles.isEmpty) {
      if (showFeedback) _showMsg("未在所选文件夹中找到音频文件");
      return 0;
    }

    final imported = await importAudioFiles(audioFiles, showFeedback: showFeedback);
    return imported;
  }

  /// Process audio files, extract tags/covers, and persist into SongDownloads Hive box
  static Future<int> importAudioFiles(List<File> files, {bool showFeedback = true}) async {
    final box = Hive.box("SongDownloads");
    final settingsCtrl = Get.isRegistered<SettingsScreenController>()
        ? Get.find<SettingsScreenController>()
        : null;
    final supportDir = settingsCtrl?.supportDirPath ?? "";

    int newlyAdded = 0;

    for (final file in files) {
      try {
        final filePath = file.path;
        final songId = "local_${filePath.hashCode.abs()}";

        // If already imported, skip
        if (box.containsKey(songId)) continue;

        Tag? tag;
        try {
          tag = await AudioTags.read(filePath);
        } catch (_) {}

        final baseName = p.basenameWithoutExtension(filePath);
        final title = (tag?.title?.trim().isNotEmpty == true)
            ? tag!.title!.trim()
            : baseName;

        final artist = (tag?.trackArtist?.trim().isNotEmpty == true)
            ? tag!.trackArtist!.trim()
            : (tag?.albumArtist?.trim().isNotEmpty == true
                ? tag!.albumArtist!.trim()
                : "本地音乐");

        final album = (tag?.album?.trim().isNotEmpty == true)
            ? tag!.album!.trim()
            : "本地曲库";

        final durationSeconds = (tag?.duration != null && tag!.duration! > 0)
            ? tag.duration!
            : 0;

        Uri artUri = Uri.parse("");
        if (tag?.pictures.isNotEmpty == true && supportDir.isNotEmpty) {
          try {
            final thumbDir = Directory("$supportDir/thumbnails");
            if (!thumbDir.existsSync()) {
              thumbDir.createSync(recursive: true);
            }
            final thumbPath = "$supportDir/thumbnails/$songId.png";
            final thumbFile = File(thumbPath);
            if (!thumbFile.existsSync()) {
              thumbFile.writeAsBytesSync(tag!.pictures.first.bytes);
            }
            artUri = Uri.file(thumbPath);
          } catch (_) {}
        }

        final durationObj = Duration(seconds: durationSeconds);
        final lengthStr = durationSeconds > 0
            ? "${durationObj.inMinutes}:${(durationObj.inSeconds % 60).toString().padLeft(2, '0')}"
            : null;

        final mediaItem = MediaItem(
          id: songId,
          title: title,
          artist: artist,
          album: album,
          duration: durationObj,
          artUri: artUri,
          extras: {
            'url': filePath,
            'length': lengthStr,
            'album': {'name': album},
            'artists': [
              {'name': artist}
            ],
            'date': DateTime.now().millisecondsSinceEpoch,
            'isLocal': true,
          },
        );

        final songJson = MediaItemBuilder.toJson(mediaItem);
        songJson['url'] = filePath;
        final fileSize = file.existsSync() ? file.lengthSync() : 0;
        songJson['streamInfo'] = [
          true,
          {
            'itag': 140,
            'audioCodec': filePath.toLowerCase().endsWith('.opus') ? 'Codec.opus' : 'Codec.mp4a',
            'bitrate': 320000,
            'duration': durationSeconds,
            'loudnessDb': 0.0,
            'url': filePath,
            'size': fileSize,
          }
        ];

        await box.put(songId, songJson);

        // Update LibrarySongsController UI list immediately
        if (Get.isRegistered<LibrarySongsController>()) {
          final libCtrl = Get.find<LibrarySongsController>();
          if (!libCtrl.librarySongsList.any((m) => m.id == songId)) {
            libCtrl.librarySongsList.add(mediaItem);
          }
        }

        newlyAdded++;
      } catch (e) {
        // Continue scanning other files
      }
    }

    if (showFeedback) {
      _showMsg("扫描完成，成功导入 $newlyAdded 首本地音乐！");
    }

    return newlyAdded;
  }

  static void _showMsg(String msg) {
    if (Get.context != null) {
      ScaffoldMessenger.of(Get.context!).showSnackBar(
        snackbar(
          Get.context!,
          msg,
          size: SanckBarSize.BIG,
          duration: const Duration(seconds: 3),
        ),
      );
    }
  }
}
