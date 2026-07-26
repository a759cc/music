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
    with TickerProviderStateMixin {
  late AnimationController _discRotationController;
  late AnimationController _waveAnimationController;
  final RxBool _isExpanded = false.obs;

  @override
  void initState() {
    super.initState();
    _discRotationController = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 12),
    );

    _waveAnimationController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1000),
    )..repeat(reverse: true);
  }

  @override
  void dispose() {
    _discRotationController.dispose();
    _waveAnimationController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final PlayerController playerController = Get.find<PlayerController>();

    return Obx(() {
      final currentSong = playerController.currentSong.value;
      if (currentSong == null) {
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
            padding: const EdgeInsets.only(top: 6.0),
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 400),
              curve: Curves.fastOutSlowIn,
              width: isExpanded
                  ? math.min(MediaQuery.of(context).size.width - 32, 360)
                  : 240,
              height: isExpanded ? 160 : 44,
              decoration: BoxDecoration(
                color: Colors.black.withOpacity(0.92),
                borderRadius: BorderRadius.circular(isExpanded ? 32 : 22),
                border: Border.all(
                  color: isPlaying
                      ? Theme.of(context).colorScheme.primary.withOpacity(0.4)
                      : Colors.white12,
                  width: 1.2,
                ),
                boxShadow: [
                  BoxShadow(
                    color: isPlaying
                        ? Theme.of(context).colorScheme.primary.withOpacity(0.35)
                        : Colors.black54,
                    blurRadius: isExpanded ? 24 : 12,
                    spreadRadius: isExpanded ? 2 : 0,
                    offset: const Offset(0, 4),
                  ),
                ],
              ),
              child: ClipRRect(
                borderRadius: BorderRadius.circular(isExpanded ? 32 : 22),
                child: Material(
                  color: Colors.transparent,
                  child: InkWell(
                    onTap: () {
                      _isExpanded.toggle();
                    },
                    child: Padding(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 10.0, vertical: 6.0),
                      child: isExpanded
                          ? _buildExpandedIsland(context, playerController, currentSong, isPlaying)
                          : _buildCollapsedIsland(context, playerController, currentSong, isPlaying),
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

  /// 展开形态 UI (Expanded Island Card)
  Widget _buildExpandedIsland(BuildContext context,
      PlayerController playerController, MediaItem song, bool isPlaying) {
    return Column(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Row(
          children: [
            // 专辑封面
            RotationTransition(
              turns: _discRotationController,
              child: Container(
                width: 52,
                height: 52,
                decoration: const BoxDecoration(
                  shape: BoxShape.circle,
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black45,
                      blurRadius: 6,
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
            const SizedBox(width: 12),
            // 歌名与歌手
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
                      fontSize: 15,
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
                      fontSize: 12,
                    ),
                  ),
                ],
              ),
            ),
            // 动态流光音浪
            SizedBox(
              width: 36,
              height: 24,
              child: AnimatedBuilder(
                animation: _waveAnimationController,
                builder: (context, child) {
                  return CustomPaint(
                    painter: AudioWaveformPainter(
                      progress: _waveAnimationController.value,
                      color: Theme.of(context).colorScheme.primary,
                      isPlaying: isPlaying,
                    ),
                  );
                },
              ),
            ),
          ],
        ),

        // 播放控制按钮栏
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceEvenly,
          children: [
            IconButton(
              icon: const Icon(Icons.skip_previous_rounded,
                  color: Colors.white, size: 28),
              onPressed: playerController.prev,
            ),
            IconButton(
              iconSize: 38,
              icon: Icon(
                isPlaying
                    ? Icons.pause_circle_filled_rounded
                    : Icons.play_circle_fill_rounded,
                color: Theme.of(context).colorScheme.primary,
              ),
              onPressed: playerController.playPause,
            ),
            IconButton(
              icon: const Icon(Icons.skip_next_rounded,
                  color: Colors.white, size: 28),
              onPressed: playerController.next,
            ),
            IconButton(
              icon: const Icon(Icons.keyboard_arrow_up_rounded,
                  color: Colors.white70, size: 24),
              onPressed: () {
                _isExpanded.value = false;
              },
            ),
          ],
        ),
      ],
    );
  }

  /// 收起形态 UI (Collapsed Island Capsule)
  Widget _buildCollapsedIsland(BuildContext context,
      PlayerController playerController, MediaItem song, bool isPlaying) {
    return Row(
      children: [
        // 旋转胶囊封面
        RotationTransition(
          turns: _discRotationController,
          child: Container(
            width: 28,
            height: 28,
            decoration: const BoxDecoration(
              shape: BoxShape.circle,
            ),
            child: ClipOval(
              child: song.artUri != null
                  ? CachedNetworkImage(
                      imageUrl: song.artUri.toString(),
                      fit: BoxFit.cover,
                      errorWidget: (context, url, error) => Container(
                        color: Colors.grey[850],
                        child: const Icon(Icons.music_note,
                            size: 16, color: Colors.white70),
                      ),
                    )
                  : Container(
                      color: Colors.grey[850],
                      child: const Icon(Icons.music_note,
                          size: 16, color: Colors.white70),
                    ),
            ),
          ),
        ),
        const SizedBox(width: 8),
        // 歌名与歌手
        Expanded(
          child: Text(
            "${song.title} • ${song.artist ?? ''}",
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(
              color: Colors.white,
              fontSize: 12,
              fontWeight: FontWeight.w600,
            ),
          ),
        ),
        const SizedBox(width: 6),
        // 3D 彩态渐变跳动音浪波形
        SizedBox(
          width: 24,
          height: 18,
          child: AnimatedBuilder(
            animation: _waveAnimationController,
            builder: (context, child) {
              return CustomPaint(
                painter: AudioWaveformPainter(
                  progress: _waveAnimationController.value,
                  color: Theme.of(context).colorScheme.primary,
                  isPlaying: isPlaying,
                ),
              );
            },
          ),
        ),
      ],
    );
  }
}

/// 绘制高质感脉冲流光音浪 (Audio Waveform Painter)
class AudioWaveformPainter extends CustomPainter {
  final double progress;
  final Color color;
  final bool isPlaying;

  AudioWaveformPainter({
    required this.progress,
    required this.color,
    required this.isPlaying,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..style = PaintingStyle.fill
      ..strokeCap = StrokeCap.round;

    final barWidth = 3.0;
    final space = 2.5;
    final count = 4;
    final totalWidth = (barWidth * count) + (space * (count - 1));
    final startX = (size.width - totalWidth) / 2;

    final List<double> heights = isPlaying
        ? [
            0.3 + 0.7 * math.sin(progress * math.pi),
            0.8 - 0.6 * math.cos(progress * math.pi * 1.5).abs(),
            0.4 + 0.6 * math.sin(progress * math.pi * 2.0).abs(),
            0.2 + 0.8 * math.cos(progress * math.pi),
          ]
        : [0.25, 0.25, 0.25, 0.25];

    final colors = [
      color,
      color.withOpacity(0.85),
      Colors.cyanAccent,
      Colors.pinkAccent,
    ];

    for (int i = 0; i < count; i++) {
      final x = startX + i * (barWidth + space);
      final currentHeight = math.max(4.0, size.height * heights[i]);
      final y = (size.height - currentHeight) / 2;

      paint.color = colors[i % colors.length];

      final rect = RRect.fromRectAndRadius(
        Rect.fromLTWH(x, y, barWidth, currentHeight),
        const Radius.circular(3),
      );

      canvas.drawRRect(rect, paint);
    }
  }

  @override
  bool shouldRepaint(covariant AudioWaveformPainter oldDelegate) {
    return oldDelegate.progress != progress ||
        oldDelegate.isPlaying != isPlaying ||
        oldDelegate.color != color;
  }
}
