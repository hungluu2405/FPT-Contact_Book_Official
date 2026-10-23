import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/schedule_item_model.dart';

/// Một ngày trong tuần; [today] do server tính theo giờ Việt Nam.
class ScheduleDayModel {
  String _date;
  bool _today;
  List<ScheduleItemModel> _items;

  ScheduleDayModel({
    required this._date,
    this._today = false,
    List<ScheduleItemModel> items = const [],
  }) : _items = List<ScheduleItemModel>.of(items);

  factory ScheduleDayModel.fromJson(Map<String, dynamic> json) {
    return ScheduleDayModel(
      date: json['date'] as String? ?? '',
      today: json['today'] as bool? ?? false,
      items: (json['items'] as List<dynamic>? ?? const [])
          .map(
            (item) => ScheduleItemModel.fromJson(item as Map<String, dynamic>),
          )
          .toList(),
    );
  }

  Map<String, dynamic> toJson() => {
    'date': _date,
    'today': _today,
    'items': [for (final item in _items) item.toJson()],
  };

  String get date => _date;
  set date(String value) => _date = value;

  bool get today => _today;
  set today(bool value) => _today = value;

  List<ScheduleItemModel> get items =>
      List<ScheduleItemModel>.unmodifiable(_items);
  set items(List<ScheduleItemModel> value) =>
      _items = List<ScheduleItemModel>.of(value);
}

/// Lịch một tuần từ thứ Hai tới Chủ nhật, theo `GET .../schedule/week`.
/// [previous] và [next] là thứ Hai của tuần trước, tuần sau.
class ScheduleWeekModel {
  String _weekStart;
  String _weekEnd;
  String _previous;
  String _next;
  List<ScheduleDayModel> _days;

  ScheduleWeekModel({
    required this._weekStart,
    required this._weekEnd,
    required this._previous,
    required this._next,
    List<ScheduleDayModel> days = const [],
  }) : _days = List<ScheduleDayModel>.of(days);

  factory ScheduleWeekModel.fromJson(Map<String, dynamic> json) {
    return ScheduleWeekModel(
      weekStart: json['weekStart'] as String? ?? '',
      weekEnd: json['weekEnd'] as String? ?? '',
      previous: json['previous'] as String? ?? '',
      next: json['next'] as String? ?? '',
      days: (json['days'] as List<dynamic>? ?? const [])
          .map((day) => ScheduleDayModel.fromJson(day as Map<String, dynamic>))
          .toList(),
    );
  }

  Map<String, dynamic> toJson() => {
    'weekStart': _weekStart,
    'weekEnd': _weekEnd,
    'previous': _previous,
    'next': _next,
    'days': [for (final day in _days) day.toJson()],
  };

  String get weekStart => _weekStart;
  set weekStart(String value) => _weekStart = value;

  String get weekEnd => _weekEnd;
  set weekEnd(String value) => _weekEnd = value;

  String get previous => _previous;
  set previous(String value) => _previous = value;

  String get next => _next;
  set next(String value) => _next = value;

  List<ScheduleDayModel> get days => List<ScheduleDayModel>.unmodifiable(_days);
  set days(List<ScheduleDayModel> value) =>
      _days = List<ScheduleDayModel>.of(value);

  /// Ngày hôm nay nếu nằm trong tuần này.
  ScheduleDayModel? get todayDay {
    for (final day in _days) {
      if (day.today) {
        return day;
      }
    }
    return null;
  }
}
