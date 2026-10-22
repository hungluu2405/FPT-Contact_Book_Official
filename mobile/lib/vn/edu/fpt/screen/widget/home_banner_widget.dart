import 'package:flutter/material.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_colors.dart';

/// Ảnh trường trên trang chủ, bo góc, tỉ lệ 16:9, căn lệch xuống để giữ
/// bảng tên trường ở đáy ảnh. Thiếu ảnh thì hiện mảng cam nhạt có logo để
/// bố cục không bị xô.
class HomeBannerWidget extends StatelessWidget {
  const HomeBannerWidget({super.key});

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(12),
      child: AspectRatio(
        aspectRatio: 16 / 9,
        child: Image.asset(
          'assets/images/home_banner.png',
          fit: BoxFit.cover,
          alignment: const Alignment(0, 0.6),
          semanticLabel: 'Ảnh trường',
          errorBuilder: (_, _, _) => Container(
            color: AppColors.orangeLight,
            alignment: Alignment.center,
            padding: const EdgeInsets.all(24),
            child: Image.asset('assets/images/fpt_schools_logo.png'),
          ),
        ),
      ),
    );
  }
}
