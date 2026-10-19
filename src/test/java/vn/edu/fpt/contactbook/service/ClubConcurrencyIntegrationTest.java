package vn.edu.fpt.contactbook.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.model.ClubActivity;
import vn.edu.fpt.contactbook.model.enums.ClubActivityStatus;
import vn.edu.fpt.contactbook.repository.ClubActivityParticipantRepository;
import vn.edu.fpt.contactbook.repository.ClubActivityRepository;
import vn.edu.fpt.contactbook.repository.ClubRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.security.AppUserDetailsService;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.support.TestDatabaseExtension;
import vn.edu.fpt.contactbook.util.AppTime;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hai thành viên cùng đăng ký chỗ cuối của một hoạt động, transaction thật (commit): chỉ một người được nhận. Test
 * không rollback nên tự xóa các hoạt động và đăng ký đã tạo. Thành viên ACTIVE của CLB 1 trong dữ liệu mẫu: users.id
 * 18 (0933112233, chủ nhiệm) và 19 (0933112234).
 */
@ExtendWith(TestDatabaseExtension.class)
@SpringBootTest
@ActiveProfiles("test")
class ClubConcurrencyIntegrationTest {

    static final int ROUNDS = 5;

    @Autowired ClubActivityService activityService;
    @Autowired AppUserDetailsService userDetailsService;
    @Autowired ClubRepository clubRepository;
    @Autowired ClubActivityRepository activityRepository;
    @Autowired ClubActivityParticipantRepository participantRepository;
    @Autowired UserRepository userRepository;
    @Autowired TransactionTemplate transactionTemplate;

    private final List<Long> createdActivities = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            for (Long id : createdActivities) {
                participantRepository.deleteAll(participantRepository.findAll().stream()
                        .filter(p -> p.getId().getActivityId().equals(id)).toList());
                activityRepository.deleteById(id);
            }
        });
    }

    @Test
    @DisplayName("Hoạt động còn 1 chỗ, hai người đăng ký cùng lúc: đúng một người thành công, sĩ số không vượt")
    void lastSeat() throws Exception {
        AppUserPrincipal first = (AppUserPrincipal) userDetailsService.loadUserByUsername("0933112233");
        AppUserPrincipal second = (AppUserPrincipal) userDetailsService.loadUserByUsername("0933112234");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                Long activityId = createActivity();
                CountDownLatch start = new CountDownLatch(1);
                List<Future<Boolean>> results = new ArrayList<>();
                for (AppUserPrincipal actor : List.of(first, second)) {
                    results.add(pool.submit(() -> {
                        start.await();
                        try {
                            activityService.register(actor, 1L, activityId);
                            return true;
                        } catch (BusinessException e) {
                            assertThat(e).hasMessage(ClubMessages.ACTIVITY_FULL);
                            return false;
                        }
                    }));
                }
                start.countDown();
                long succeeded = 0;
                for (Future<Boolean> result : results) {
                    succeeded += result.get(60, TimeUnit.SECONDS) ? 1 : 0;
                }
                assertThat(succeeded).as("round %d", round).isEqualTo(1);
                assertThat(participantRepository.countTaken(activityId)).as("round %d", round).isEqualTo(1);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private Long createActivity() {
        Long id = transactionTemplate.execute(status -> {
            ClubActivity activity = new ClubActivity();
            activity.setClub(clubRepository.getReferenceById(1L));
            activity.setTitle("Chỗ cuối");
            activity.setStartTime(AppTime.now().plusDays(1).withNano(0));
            activity.setEndTime(AppTime.now().plusDays(1).plusHours(2).withNano(0));
            activity.setMaxParticipants((short) 1);
            activity.setStatus(ClubActivityStatus.PLANNED);
            activity.setCreatedBy(userRepository.getReferenceById(18L));
            return activityRepository.saveAndFlush(activity).getId();
        });
        createdActivities.add(id);
        return id;
    }
}
