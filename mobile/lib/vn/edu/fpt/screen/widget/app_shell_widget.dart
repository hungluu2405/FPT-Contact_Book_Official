import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_colors.dart';

/// Một tab của [AppShellWidget]: icon, nhãn, màn hình gốc và số trên nhãn
/// đỏ (null hoặc 0 là không hiện).
class AppShellTab {
  final IconData icon;
  final IconData selectedIcon;
  final String label;
  final WidgetBuilder rootBuilder;
  final int? Function()? badgeCount;

  const AppShellTab({
    required this.icon,
    required this.selectedIcon,
    required this.label,
    required this.rootBuilder,
    this.badgeCount,
  });
}

/// Khung dùng chung của app sau đăng nhập: thanh điều hướng dưới và mỗi tab
/// một ngăn điều hướng riêng. Màn hình mở bằng `Navigator.of(context).push`
/// từ trong tab nằm trong ngăn của tab đó, nên thanh dưới luôn hiện.
///
/// - Đổi tab giữ nguyên màn hình đang mở của từng tab; bấm lại tab đang mở
///   thì về màn hình gốc của tab (màn hình đang soạn dở hỏi trước).
/// - Nút back của hệ thống: tab còn màn hình con thì lùi trong tab; đang ở
///   gốc của tab khác thì về tab đầu; đang ở gốc tab đầu thì thoát app.
/// - Tab chỉ được dựng khi mở lần đầu; [onTabOpened] báo mỗi lần chọn tab.
class AppShellWidget extends StatefulWidget {
  final List<AppShellTab> tabs;

  /// Thay đổi thì vẽ lại thanh dưới (ví dụ số thông báo chưa đọc).
  final Listenable? badgeListenable;
  final ValueChanged<int>? onTabOpened;

  const AppShellWidget({
    super.key,
    required this.tabs,
    this.badgeListenable,
    this.onTabOpened,
  });

  @override
  State<AppShellWidget> createState() => _AppShellWidgetState();
}

class _AppShellWidgetState extends State<AppShellWidget> {
  late final List<GlobalKey<NavigatorState>> _navigatorKeys = [
    for (var i = 0; i < widget.tabs.length; i++) GlobalKey<NavigatorState>(),
  ];
  final Set<int> _opened = {0};
  int _tab = 0;

  void _select(int index) {
    if (index == _tab) {
      _popToRoot(_navigatorKeys[index].currentState);
    } else {
      setState(() {
        _tab = index;
        _opened.add(index);
      });
    }
    widget.onTabOpened?.call(index);
  }

  /// Về màn hình gốc của tab, nhưng dừng ở màn hình đang soạn dở (không cho
  /// đóng thẳng) và để màn hình đó hỏi: đồng ý bỏ thì chỉ đóng màn hình đó.
  void _popToRoot(NavigatorState? navigator) {
    if (navigator == null) {
      return;
    }
    navigator.popUntil(
      (route) =>
          route.isFirst || route.popDisposition == RoutePopDisposition.doNotPop,
    );
    if (navigator.canPop()) {
      navigator.maybePop();
    }
  }

  void _handleBack() {
    final navigator = _navigatorKeys[_tab].currentState;
    if (navigator != null && navigator.canPop()) {
      // maybePop để màn hình đang soạn dở được hỏi trước khi đóng.
      navigator.maybePop();
    } else if (_tab != 0) {
      _select(0);
    } else {
      SystemNavigator.pop();
    }
  }

  @override
  Widget build(BuildContext context) {
    return PopScope<Object?>(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) {
          _handleBack();
        }
      },
      child: Scaffold(
        body: IndexedStack(
          index: _tab,
          children: [
            for (var i = 0; i < widget.tabs.length; i++)
              if (_opened.contains(i)) _tabNavigator(i) else const SizedBox(),
          ],
        ),
        bottomNavigationBar: widget.badgeListenable == null
            ? _bar()
            : ListenableBuilder(
                listenable: widget.badgeListenable!,
                builder: (context, _) => _bar(),
              ),
      ),
    );
  }

  /// Ngăn điều hướng của một tab. Mỗi ngăn không dùng chung HeroController
  /// của MaterialApp (một controller chỉ gắn được với một Navigator).
  Widget _tabNavigator(int index) {
    return HeroControllerScope.none(
      child: Navigator(
        key: _navigatorKeys[index],
        onGenerateRoute: (settings) => MaterialPageRoute<void>(
          settings: settings,
          builder: widget.tabs[index].rootBuilder,
        ),
      ),
    );
  }

  Widget _bar() {
    return NavigationBar(
      selectedIndex: _tab,
      onDestinationSelected: _select,
      backgroundColor: Colors.white,
      indicatorColor: AppColors.orangeLight,
      surfaceTintColor: Colors.transparent,
      destinations: [
        for (final tab in widget.tabs)
          NavigationDestination(
            icon: _withBadge(tab, Icon(tab.icon)),
            selectedIcon: _withBadge(
              tab,
              Icon(tab.selectedIcon, color: AppColors.orange),
            ),
            label: tab.label,
          ),
      ],
    );
  }

  Widget _withBadge(AppShellTab tab, Widget icon) {
    final count = tab.badgeCount?.call() ?? 0;
    if (count <= 0) {
      return icon;
    }
    return Badge(label: Text(count > 99 ? '99+' : '$count'), child: icon);
  }
}
