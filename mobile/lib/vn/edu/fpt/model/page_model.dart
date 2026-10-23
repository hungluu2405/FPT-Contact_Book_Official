/// Một trang của danh sách, theo dạng phân trang của docs/API.md:
/// `{items, page, size, totalElements, totalPages}`, [page] đếm từ 0.
class PageModel<T> {
  List<T> _items;
  int _page;
  int _size;
  int _totalElements;
  int _totalPages;

  PageModel({
    required List<T> items,
    required this._page,
    required this._size,
    required this._totalElements,
    required this._totalPages,
  }) : _items = List<T>.of(items);

  factory PageModel.fromJson(
    Map<String, dynamic> json,
    T Function(Map<String, dynamic> item) itemFromJson,
  ) {
    return PageModel(
      items: (json['items'] as List<dynamic>? ?? const [])
          .map((item) => itemFromJson(item as Map<String, dynamic>))
          .toList(),
      page: (json['page'] as num?)?.toInt() ?? 0,
      size: (json['size'] as num?)?.toInt() ?? 0,
      totalElements: (json['totalElements'] as num?)?.toInt() ?? 0,
      totalPages: (json['totalPages'] as num?)?.toInt() ?? 0,
    );
  }

  List<T> get items => List<T>.unmodifiable(_items);
  set items(List<T> value) => _items = List<T>.of(value);

  int get page => _page;
  set page(int value) => _page = value;

  int get size => _size;
  set size(int value) => _size = value;

  int get totalElements => _totalElements;
  set totalElements(int value) => _totalElements = value;

  int get totalPages => _totalPages;
  set totalPages(int value) => _totalPages = value;

  /// Còn trang sau để tải thêm.
  bool get hasMore => _page + 1 < _totalPages;
}
