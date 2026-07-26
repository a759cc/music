import 'dart:math' as math;
import 'package:audio_service/audio_service.dart';
import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';
import '/ui/player/player_controller.dart';

class DynamicIslandWidget extends StatefulWidget {
  const DynamicIslandWidget({super.key});

  @override
  State<DynamicIslandWidget> createState() => _DynamicIslandWidgetState();
}

class _DynamicIslandWidgetState extends State<DynamicIslandWidget>
    with TickerProviderStateMixin, WidgetsBindingObserver {
  late AnimationController _discRotationController;
  late AnimationController _waveAnimationController;
  final RxBool _isExpanded = false.obs;
  final RxBool _isInBackground = false.obs;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _discRotationController = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 12),
    );

    _waveAnimationController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 800),
    )..repeat(reverse: true);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _discRotationController.dispose();
    _waveAnimationController.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.paused ||
        state == AppLifecycleState.hidden ||
        state == AppLifecycleState.inactive) {
      _isInBackground.value = true;
    } else if (state == AppLifecycleState.resumed) {
      _isInBackground.value = false;
      _isExpanded.value = false;
    }
  }

  @override
  Widget build(BuildContext context) {
    final PlayerController playerController = Get.find<PlayerController>();

    return Obx(() {
      final currentSong = playerController.currentSong.value;
      final isInBackground = _isInBackground.value;

      // 仅在退到后台且播放音乐时在顶部状态栏浮现灵动岛
      if (currentSong == null || !isInBackground) {
        _discRotationController.stop();
        return const SizedBox.shrink();
      }

      final isPlaying =
          playerController.buttonState.value == PlayButtonState.playing;

      if (isPlaying) {
        if (!_discRotationController.isAnimating) {
          _discRotationController.repeat();
        }
        if (!_waveAnimationController.isAnimating) {
          _waveAnimationController.repeat(reverse: true);
        }
      } else {
        _discRotationController.stop();
        _waveAnimationController.stop();
      }

      final isExpanded = _isExpanded.value;

      return SafeArea(
        child: Align(
          alignment: Alignment.topCenter,
          child: Padding(
            padding: const EdgeInsets.only(top: 0.0),
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 320),
              curve: Curves.fastOutSlowIn,
              width: isExpanded
                  ? math.min(MediaQuery.of(context).size.width - 24, 340)
                  : 148, // 完美还原截图尺寸：148px 宽胶囊
              height: isExpanded ? 165 : 34, // 34px 高度精准匹配顶部 Status Bar 状态栏
              decoration: BoxDecoration(
                color: Colors.black,
                borderRadius: BorderRadius.circular(isExpanded ? 28 : 17),
                border: Border.all(
                  color: Colors.white.withOpacity(0.15),
                  width: 0.8,
                ),
                boxShadow: const [
                  BoxShadow(
                    color: Colors.black87,
                    blurRadius: 10,
                    spreadRadius: 1,
                    offset: Offset(0, 2),
                  ),
                ],
              ),
              child: ClipRRect(
                borderRadius: BorderRadius.circular(isExpanded ? 28 : 17),
                child: Material(
                  color: Colors.transparent,
                  child: InkWell(
                    onTap: () {
                      _isExpanded.toggle();
                    },
                    child: Padding(
                      padding: EdgeInsets.symmetric(
                        horizontal: isExpanded ? 12.0 : 6.0,
                        vertical: isExpanded ? 8.0 : 4.0,
                      ),
                      child: isExpanded
                          ? _buildExpandedIsland(context, playerController, currentSong, isPlaying)
                          : _buildExactScreenshotMiniIsland(context, playerController, currentSong, isPlaying),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      );
    });
  }

  /// 1:1 还原截图样式的极简收起态胶囊 (Mini Capsule Matching User Screenshot)
  Widget _buildExactScreenshotMiniIsland(BuildContext context,
      PlayerController playerController, MediaItem song, bool isPlaying) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        // 左侧：26px 原生风格圆形专辑小头像
        RotationTransition(
          turns: _discRotationController,
          child: Container(
            width: 26,
            height: 26,
            decoration: const BoxDecoration(
              shape: BoxShape.circle,
              color: Colors.black,
            ),
            child: ClipOval(
              child: song.artUri != null
                  ? CachedNetworkImage(
                      imageUrl: song.artUri.toString(),
                      fit: BoxFit.cover,
                      errorWidget: (context, url, error) => Container(
                        color: Colors.grey[900],
                        child: const Icon(Icons.music_note,
                            size: 14, color: Colors.white70),
                      ),
                    )
                  : Container(
                      color: Colors.grey[900],
                      child: const Icon(Icons.music_note,
                          size: 14, color: Colors.white70),
                    ),
            ),
          ),
        ),
        
        // 右侧：截图同款纯白跳动波浪律动条 (White Audio Waveform)
        Padding(
          padding: const EdgeInsets.only(right: 4.0),
          child: SizedBox(
            width: 22,
            height: 16,
            child: AnimatedBuilder(
              animation: _waveAnimationController,
              builder: (context, child) {
                return CustomPaint(
                  painter: ScreenshotWhiteWaveformPainter(
                    progress: _waveAnimationController.value,
                    isPlaying: isPlaying,
                  ),
                );
              },
            ),
          ),
        ),
      ],
    );
  }

  /// 展开形态 UI (Expanded Island with Slider & Controls)
  Widget _buildExpandedIsland(BuildContext context,
      PlayerController playerController, MediaItem song, bool isPlaying) {
    return Column(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Row(
          children: [
            RotationTransition(
              turns: _discRotationController,
              child: Container(
                width: 44,
                height: 44,
                decoration: const BoxDecoration(
                  shape: BoxShape.circle,
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black45,
                      blurRadius: 4,
                    ),
                  ],
                ),
                child: ClipOval(
                  child: song.artUri != null
                      ? CachedNetworkImage(
                          imageUrl: song.artUri.toString(),
                          fit: BoxFit.cover,
                          errorWidget: (context, url, error) => Container(
                            color: Colors.grey[900],
                            child: const Icon(Icons.music_note, color: Colors.white70),
                          ),
                        )
                      : Container(
                          color: Colors.grey[900],
                          child: const Icon(Icons.music_note, color: Colors.white70),
                        ),
                ),
              ),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Text(
                    song.title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 14,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    song.artist ?? "Unknown Artist",
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      color: Colors.white70,
                      fontSize: 11,
                    ),
                  ),
                ],
              ),
            ),
            SizedBox(
              width: 28,
              height: 18,
              child: AnimatedBuilder(
                animation: _waveAnimationController,
                builder: (context, child) {
                  return CustomPaint(
                    painter: ScreenshotWhiteWaveformPainter(
                      progress: _waveAnimationController.value,
                      isPlaying: isPlaying,
                    ),
                  );
                },
              ),
            ),
          ],
        ),

        // 播放进度条
        Obx(() {
          final position = playerController.progressBarStatus.value.current;
          final duration = playerController.progressBarStatus.value.total;
          final double maxSec = duration.inSeconds > 0 ? duration.inSeconds.toDouble() : 1.0;
          final double currentSec = math.min<double>(position.inSeconds.toDouble(), maxSec);

          return Column(
            children: [
              SliderTheme(
                data: SliderThemeData(
                  trackHeight: 3.0,
                  thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 6),
                  overlayShape: const RoundSliderOverlayShape(overlayRadius: 10),
                  activeTrackColor: Theme.of(context).colorScheme.primary,
                  inactiveTrackColor: Colors.white24,
                  thumbColor: Colors.white,
                ),
                child: Slider(
                  value: currentSec,
                  max: maxSec,
                  onChanged: (val) {
                    playerController.seek(Duration(seconds: val.toInt()));
                  },
                ),
              ),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 8.0),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      _formatDuration(position),
                      style: const TextStyle(color: Colors.white54, fontSize: 10),
                    ),
                    Text(
                      _formatDuration(duration),
                      style: const TextStyle(color: Colors.white54, fontSize: 10),
                    ),
                  ],
                ),
              ),
            ],
          );
        }),

        // 控件按键
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceEvenly,
          children: [
            IconButton(
              padding: EdgeInsets.zero,
              constraints: const BoxConstraints(),
              icon: const Icon(Icons.skip_previous_rounded,
                  color: Colors.white, size: 26),
              onPressed: playerController.prev,
            ),
            IconButton(
              padding: EdgeInsets.zero,
              constraints: const BoxConstraints(),
              iconSize: 34,
              icon: Icon(
                isPlaying
                    ? Icons.pause_circle_filled_rounded
                    : Icons.play_circle_fill_rounded,
                color: Theme.of(context).colorScheme.primary,
              ),
              onPressed: playerController.playPause,
            ),
            IconButton(
              padding: EdgeInsets.zero,
              constraints: const BoxConstraints(),
              icon: const Icon(Icons.skip_next_rounded,
                  color: Colors.white, size: 26),
              onPressed: playerController.next,
            ),
            IconButton(
              padding: EdgeInsets.zero,
              constraints: const BoxConstraints(),
              icon: const Icon(Icons.keyboard_arrow_up_rounded,
                  color: Colors.white70, size: 22),
              onPressed: () {
                _isExpanded.value = false;
              },
            ),
          ],
        ),
      ],
    );
  }

  String _formatDuration(Duration duration) {
    String twoDigits(int n) => n.toString().padLeft(2, "0");
    final minutes = twoDigits(duration.inMinutes.remainder(60));
    final seconds = twoDigits(duration.inSeconds.remainder(60));
    return "$minutes:$seconds";
  }
}

/// 绘制截图同款纯白跳动律动条 (Screenshot Pure White Waveform Painter)
class ScreenshotWhiteWaveformPainter extends CustomPainter {
  final double progress;
  final bool isPlaying;

  ScreenshotWhiteWaveformPainter({
    required this.progress,
    required this.isPlaying,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = Colors.white
      ..style = PaintingStyle.fill
      ..strokeCap = StrokeCap.round;

    final barWidth = 2.5;
    final space = 2.0;
    final count = 4;
    final totalWidth = (barWidth * count) + (space * (count - 1));
    final startX = (size.width - totalWidth) / 2;

    final List<double> heights = isPlaying
        ? [
            0.35 + 0.65 * math.sin(progress * math.pi),
            0.85 - 0.65 * math.cos(progress * math.pi * 1.4).abs(),
            0.45 + 0.55 * math.sin(progress * math.pi * 1.8).abs(),
            0.25 + 0.75 * math.cos(progress * math.pi),
          ]
        : [0.3, 0.3, 0.3, 0.3];

    for (int i = 0; i < count; i++) {
      final x = startX + i * (barWidth + space);
      final currentHeight = math.max(3.5, size.height * heights[i]);
      final y = (size.height - currentHeight) / 2;

      final rect = RRect.fromRectAndRadius(
        Rect.fromLTWH(x, y, barWidth, currentHeight),
        const Radius.circular(2),
      );

      canvas.drawRRect(rect, paint);
    }
  }

  @override
  bool shouldRepaint(covariant ScreenshotWhiteWaveformPainter oldDelegate) {
    return oldDelegate.progress != progress ||
        oldDelegate.isPlaying != isPlaying;
  }
}
