package vn.edu.fpt.contactbook.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.fpt.contactbook.model.ConversationMemberId;
import vn.edu.fpt.contactbook.repository.ConversationMemberRepository;
import vn.edu.fpt.contactbook.repository.ConversationRepository;
import vn.edu.fpt.contactbook.repository.MessageRepository;
import vn.edu.fpt.contactbook.security.AppUserDetailsService;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.support.TestAccounts;
import vn.edu.fpt.contactbook.support.TestDatabaseExtension;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "Tìm hoặc tạo" hội thoại DIRECT chạy đồng thời với transaction thật (commit), nên test này KHÔNG rollback mà tự
 * dọn hội thoại, thành viên và tin nhắn của cặp phụ huynh 0900000005 - giảng viên 0900000003 sau khi chạy.
 */
@ExtendWith(TestDatabaseExtension.class)
@SpringBootTest
@ActiveProfiles("test")
class MessageConcurrencyIntegrationTest {

    static final int THREADS = 8;

    @Autowired MessageService messageService;
    @Autowired AppUserDetailsService userDetailsService;
    @Autowired ConversationRepository conversationRepository;
    @Autowired ConversationMemberRepository memberRepository;
    @Autowired MessageRepository messageRepository;
    @Autowired TransactionTemplate transactionTemplate;

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            for (Long id : conversationRepository.findDirectIds(TestAccounts.PARENT_ID, TestAccounts.TEACHER_ID)) {
                messageRepository.deleteAll(messageRepository.findByConversationIdOrderByIdDesc(id,
                        PageRequest.of(0, 1000)));
                memberRepository.deleteAllById(List.of(new ConversationMemberId(id, TestAccounts.PARENT_ID),
                        new ConversationMemberId(id, TestAccounts.TEACHER_ID)));
                conversationRepository.deleteById(id);
            }
        });
    }

    @Test
    @DisplayName("Hai bên cùng gửi tin đầu tiên một lúc: chỉ một hội thoại, đủ mọi tin")
    void concurrentFirstMessages() throws Exception {
        AppUserPrincipal parent = (AppUserPrincipal) userDetailsService.loadUserByUsername(TestAccounts.PARENT);
        AppUserPrincipal teacher = (AppUserPrincipal) userDetailsService.loadUserByUsername(TestAccounts.TEACHER);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        List<Future<Long>> results = new ArrayList<>();
        try {
            for (int i = 0; i < THREADS; i++) {
                boolean fromParent = i % 2 == 0;
                String content = "Tin " + i;
                results.add(pool.submit(() -> {
                    start.await();
                    return fromParent ? messageService.sendTo(parent, TestAccounts.TEACHER_ID, content)
                            : messageService.sendTo(teacher, TestAccounts.PARENT_ID, content);
                }));
            }
            start.countDown();
            Set<Long> conversationIds = new HashSet<>();
            for (Future<Long> result : results) {
                conversationIds.add(result.get(60, TimeUnit.SECONDS));
            }

            List<Long> stored = conversationRepository.findDirectIds(TestAccounts.PARENT_ID, TestAccounts.TEACHER_ID);
            assertThat(stored).hasSize(1);
            assertThat(conversationIds).containsExactly(stored.getFirst());
            assertThat(messageRepository.findByConversationIdOrderByIdDesc(stored.getFirst(), PageRequest.of(0, 100)))
                    .hasSize(THREADS);
        } finally {
            pool.shutdownNow();
        }
    }
}
