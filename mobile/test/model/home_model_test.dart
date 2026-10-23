import 'package:flutter_test/flutter_test.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/child_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/profile_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/user_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/date_format.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/user_labels.dart';

import '../support/fakes.dart';

void main() {
  group('ChildModel', () {
    test('reads the fields of GET /parent/children', () {
      final child = ChildModel.fromJson(childJson(id: 7, code: 'SE190002'));

      expect(child.id, 7);
      expect(child.code, 'SE190002');
      expect(child.name, 'Nguyễn Văn An');
      expect(child.toJson(), childJson(id: 7, code: 'SE190002'));
    });
  });

  group('ProfileModel', () {
    test('reads the profile and its roles', () {
      final profile = ProfileModel.fromJson(
        profileJson(
          roles: [
            {
              'roleCode': 'HEAD_OF_DEPARTMENT',
              'departmentCode': 'TT',
              'departmentName': 'Tổ Toán – Tin học',
            },
          ],
        ),
      );

      expect(profile.phone, '0900000004');
      expect(profile.email, 'an.nv@fpt.edu.vn');
      expect(profile.gender, 'MALE');
      expect(profile.dateOfBirth, '2005-03-15');
      expect(profile.roles.single.roleCode, 'HEAD_OF_DEPARTMENT');
      expect(profile.roles.single.departmentName, 'Tổ Toán – Tin học');
    });

    test('keeps missing optional fields as null', () {
      final profile = ProfileModel.fromJson({
        'phone': '0900000005',
        'fullName': 'Lê Văn Phụ',
        'email': null,
        'gender': null,
        'dateOfBirth': null,
        'address': null,
      });

      expect(profile.email, isNull);
      expect(profile.gender, isNull);
      expect(profile.dateOfBirth, isNull);
      expect(profile.roles, isEmpty);
    });
  });

  group('formatApiDate', () {
    test('turns yyyy-MM-dd into dd/MM/yyyy', () {
      expect(formatApiDate('2005-03-15'), '15/03/2005');
    });

    test('returns empty text for null or empty', () {
      expect(formatApiDate(null), '');
      expect(formatApiDate(''), '');
    });

    test('keeps an unexpected value as it is', () {
      expect(formatApiDate('15/03/2005'), '15/03/2005');
    });
  });

  group('user labels', () {
    test('initialOf takes the first letter of the given name', () {
      expect(initialOf('Nguyễn Văn An'), 'A');
      expect(initialOf('  lê   minh   ước '), 'Ư');
      expect(initialOf(''), '?');
    });

    test('roleLabelsOf joins Vietnamese role names', () {
      final user = UserModel(
        id: 2,
        phone: '0900000002',
        fullName: 'Trần Thị Bình',
        roles: [RoleCodes.teacher, RoleCodes.headOfDepartment],
      );

      expect(roleLabelsOf(user), 'Giáo viên, Tổ trưởng chuyên môn');
      expect(roleLabelsOf(null), '');
    });
  });
}
