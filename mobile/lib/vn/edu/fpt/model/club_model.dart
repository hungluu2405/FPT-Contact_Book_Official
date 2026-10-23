/// Mã vai trò và trạng thái thành viên CLB (giống enum của backend).
class ClubMemberRole {
  ClubMemberRole._();

  static const String president = 'PRESIDENT';
  static const String vicePresident = 'VICE_PRESIDENT';
  static const String member = 'MEMBER';
}

class ClubMemberStatus {
  ClubMemberStatus._();

  static const String pending = 'PENDING';
  static const String active = 'ACTIVE';
  static const String rejected = 'REJECTED';
  static const String left = 'LEFT';
}

/// Một câu lạc bộ theo `GET /clubs` (ClubResponse). [status] là `ACTIVE`
/// hoặc `INACTIVE`; ngày thành lập dạng `yyyy-MM-dd`.
class ClubModel {
  int _id;
  String _code;
  String _name;
  String? _description;
  String? _foundedDate;
  String? _advisorName;
  String _status;
  int _memberCount;
  String? _presidentName;

  ClubModel({
    required this._id,
    required this._code,
    required this._name,
    this._description,
    this._foundedDate,
    this._advisorName,
    this._status = 'ACTIVE',
    this._memberCount = 0,
    this._presidentName,
  });

  factory ClubModel.fromJson(Map<String, dynamic> json) {
    return ClubModel(
      id: (json['id'] as num).toInt(),
      code: json['code'] as String? ?? '',
      name: json['name'] as String? ?? '',
      description: json['description'] as String?,
      foundedDate: json['foundedDate'] as String?,
      advisorName: json['advisorName'] as String?,
      status: json['status'] as String? ?? 'ACTIVE',
      memberCount: (json['memberCount'] as num?)?.toInt() ?? 0,
      presidentName: json['presidentName'] as String?,
    );
  }

  Map<String, dynamic> toJson() => {
    'id': _id,
    'code': _code,
    'name': _name,
    'description': _description,
    'foundedDate': _foundedDate,
    'advisorName': _advisorName,
    'status': _status,
    'memberCount': _memberCount,
    'presidentName': _presidentName,
  };

  int get id => _id;
  set id(int value) => _id = value;

  String get code => _code;
  set code(String value) => _code = value;

  String get name => _name;
  set name(String value) => _name = value;

  String? get description => _description;
  set description(String? value) => _description = value;

  String? get foundedDate => _foundedDate;
  set foundedDate(String? value) => _foundedDate = value;

  String? get advisorName => _advisorName;
  set advisorName(String? value) => _advisorName = value;

  String get status => _status;
  set status(String value) => _status = value;

  int get memberCount => _memberCount;
  set memberCount(int value) => _memberCount = value;

  String? get presidentName => _presidentName;
  set presidentName(String? value) => _presidentName = value;

  bool get isActive => _status == 'ACTIVE';
}

/// Chi tiết CLB và quan hệ của người xem; [myStatus], [myRole] null khi
/// chưa từng gửi yêu cầu tham gia.
class ClubDetailModel {
  ClubModel _club;
  String? _myStatus;
  String? _myRole;

  ClubDetailModel({required this._club, this._myStatus, this._myRole});

  factory ClubDetailModel.fromJson(Map<String, dynamic> json) {
    return ClubDetailModel(
      club: ClubModel.fromJson(json['club'] as Map<String, dynamic>),
      myStatus: json['myStatus'] as String?,
      myRole: json['myRole'] as String?,
    );
  }

  ClubModel get club => _club;
  set club(ClubModel value) => _club = value;

  String? get myStatus => _myStatus;
  set myStatus(String? value) => _myStatus = value;

  String? get myRole => _myRole;
  set myRole(String? value) => _myRole = value;
}

/// Một thành viên đang tham gia: họ tên, vai trò, thời điểm tham gia.
class ClubMemberModel {
  String _fullName;
  String _role;
  String? _joinedAt;

  ClubMemberModel({
    required this._fullName,
    required this._role,
    this._joinedAt,
  });

  factory ClubMemberModel.fromJson(Map<String, dynamic> json) {
    return ClubMemberModel(
      fullName: json['fullName'] as String? ?? '',
      role: json['role'] as String? ?? ClubMemberRole.member,
      joinedAt: json['joinedAt'] as String?,
    );
  }

  String get fullName => _fullName;
  set fullName(String value) => _fullName = value;

  String get role => _role;
  set role(String value) => _role = value;

  /// Thời điểm `yyyy-MM-ddTHH:mm:ss`.
  String? get joinedAt => _joinedAt;
  set joinedAt(String? value) => _joinedAt = value;
}

/// Một hoạt động sắp tới. Thời gian dạng `yyyy-MM-ddTHH:mm:ss`; [state] là
/// `PLANNED`, `ONGOING`, `DONE` hoặc `CANCELLED`; [myStatus] là đăng ký của
/// người xem (null là chưa đăng ký).
class ClubActivityModel {
  int _id;
  String _title;
  String? _description;
  String? _location;
  String _startTime;
  String _endTime;
  int? _maxParticipants;
  int _taken;
  String _state;
  String? _myStatus;

  ClubActivityModel({
    required this._id,
    required this._title,
    this._description,
    this._location,
    required this._startTime,
    required this._endTime,
    this._maxParticipants,
    this._taken = 0,
    this._state = 'PLANNED',
    this._myStatus,
  });

  factory ClubActivityModel.fromJson(Map<String, dynamic> json) {
    return ClubActivityModel(
      id: (json['id'] as num).toInt(),
      title: json['title'] as String? ?? '',
      description: json['description'] as String?,
      location: json['location'] as String?,
      startTime: json['startTime'] as String? ?? '',
      endTime: json['endTime'] as String? ?? '',
      maxParticipants: (json['maxParticipants'] as num?)?.toInt(),
      taken: (json['taken'] as num?)?.toInt() ?? 0,
      state: json['state'] as String? ?? 'PLANNED',
      myStatus: json['myStatus'] as String?,
    );
  }

  int get id => _id;
  set id(int value) => _id = value;

  String get title => _title;
  set title(String value) => _title = value;

  String? get description => _description;
  set description(String? value) => _description = value;

  String? get location => _location;
  set location(String? value) => _location = value;

  String get startTime => _startTime;
  set startTime(String value) => _startTime = value;

  String get endTime => _endTime;
  set endTime(String value) => _endTime = value;

  int? get maxParticipants => _maxParticipants;
  set maxParticipants(int? value) => _maxParticipants = value;

  int get taken => _taken;
  set taken(int value) => _taken = value;

  String get state => _state;
  set state(String value) => _state = value;

  String? get myStatus => _myStatus;
  set myStatus(String? value) => _myStatus = value;
}

/// Một CLB trong "CLB của tôi / của con": vai trò và trạng thái tham gia.
class MyClubModel {
  int _clubId;
  String _code;
  String _name;
  String _clubStatus;
  String _role;
  String _status;

  MyClubModel({
    required this._clubId,
    required this._code,
    required this._name,
    required this._clubStatus,
    required this._role,
    required this._status,
  });

  factory MyClubModel.fromJson(Map<String, dynamic> json) {
    return MyClubModel(
      clubId: (json['clubId'] as num).toInt(),
      code: json['code'] as String? ?? '',
      name: json['name'] as String? ?? '',
      clubStatus: json['clubStatus'] as String? ?? 'ACTIVE',
      role: json['role'] as String? ?? ClubMemberRole.member,
      status: json['status'] as String? ?? ClubMemberStatus.pending,
    );
  }

  int get clubId => _clubId;
  set clubId(int value) => _clubId = value;

  String get code => _code;
  set code(String value) => _code = value;

  String get name => _name;
  set name(String value) => _name = value;

  String get clubStatus => _clubStatus;
  set clubStatus(String value) => _clubStatus = value;

  String get role => _role;
  set role(String value) => _role = value;

  String get status => _status;
  set status(String value) => _status = value;
}
