/// Một đầu điểm của môn. [score] null là chưa có điểm hoặc chưa công bố.
class GradeCellModel {
  String _componentName;
  double _weight;
  double _maxScore;
  double? _score;

  GradeCellModel({
    required this._componentName,
    required this._weight,
    required this._maxScore,
    this._score,
  });

  factory GradeCellModel.fromJson(Map<String, dynamic> json) {
    return GradeCellModel(
      componentName: json['componentName'] as String? ?? '',
      weight: (json['weight'] as num?)?.toDouble() ?? 0,
      maxScore: (json['maxScore'] as num?)?.toDouble() ?? 10,
      score: (json['score'] as num?)?.toDouble(),
    );
  }

  Map<String, dynamic> toJson() => {
    'componentName': _componentName,
    'weight': _weight,
    'maxScore': _maxScore,
    'score': _score,
  };

  String get componentName => _componentName;
  set componentName(String value) => _componentName = value;

  /// Trọng số theo phần trăm, ví dụ 20.
  double get weight => _weight;
  set weight(double value) => _weight = value;

  double get maxScore => _maxScore;
  set maxScore(double value) => _maxScore = value;

  double? get score => _score;
  set score(double? value) => _score = value;
}

/// Điểm của một môn trong học kỳ (StudentGradeRow). [average] null khi còn
/// đầu điểm chưa có điểm hoặc môn chưa có đầu điểm nào.
class GradeRowModel {
  String _sectionCode;
  String _subjectCode;
  String _subjectName;
  List<GradeCellModel> _cells;
  double? _average;

  GradeRowModel({
    required this._sectionCode,
    required this._subjectCode,
    required this._subjectName,
    List<GradeCellModel> cells = const [],
    this._average,
  }) : _cells = List<GradeCellModel>.of(cells);

  factory GradeRowModel.fromJson(Map<String, dynamic> json) {
    return GradeRowModel(
      sectionCode: json['sectionCode'] as String? ?? '',
      subjectCode: json['subjectCode'] as String? ?? '',
      subjectName: json['subjectName'] as String? ?? '',
      cells: (json['cells'] as List<dynamic>? ?? const [])
          .map((cell) => GradeCellModel.fromJson(cell as Map<String, dynamic>))
          .toList(),
      average: (json['average'] as num?)?.toDouble(),
    );
  }

  Map<String, dynamic> toJson() => {
    'sectionCode': _sectionCode,
    'subjectCode': _subjectCode,
    'subjectName': _subjectName,
    'cells': [for (final cell in _cells) cell.toJson()],
    'average': _average,
  };

  String get sectionCode => _sectionCode;
  set sectionCode(String value) => _sectionCode = value;

  String get subjectCode => _subjectCode;
  set subjectCode(String value) => _subjectCode = value;

  String get subjectName => _subjectName;
  set subjectName(String value) => _subjectName = value;

  List<GradeCellModel> get cells => List<GradeCellModel>.unmodifiable(_cells);
  set cells(List<GradeCellModel> value) =>
      _cells = List<GradeCellModel>.of(value);

  double? get average => _average;
  set average(double? value) => _average = value;
}
