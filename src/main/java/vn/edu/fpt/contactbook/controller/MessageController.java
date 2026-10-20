package vn.edu.fpt.contactbook.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.fpt.contactbook.dto.ChatView;
import vn.edu.fpt.contactbook.dto.ContactView;
import vn.edu.fpt.contactbook.dto.ConversationListItem;
import vn.edu.fpt.contactbook.dto.MessageForm;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.MessageService;
import vn.edu.fpt.contactbook.util.PageUtils;

/**
 * Tin nhắn (SRS FR-MSG-01, 02, 04): danh sách hội thoại bên trái, khung chat bên phải, danh bạ "Nhắn tin mới". Làm
 * mới tự động và tải tin cũ trả MẢNH HTML (fragment Thymeleaf), không trả JSON. Quyền và quy tắc nhắn tin kiểm tra ở
 * MessageService; SecurityConfig chỉ cho SV, PH, GV, TBM vào /messages.
 */
@Controller
@RequestMapping(MessageController.BASE)
@RequiredArgsConstructor
public class MessageController {

    static final String BASE = "/messages";
    private static final String VIEW = "messages/index";
    private static final String FRAGMENTS = "messages/fragments";

    private final MessageService messageService;

    /** Bỏ khoảng trắng hai đầu trước khi kiểm tra độ dài; chuỗi rỗng thành null. */
    @InitBinder("form")
    void trimStrings(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping
    public String index(@AuthenticationPrincipal AppUserPrincipal actor, @RequestParam(defaultValue = "0") int page,
                        Model model) {
        return view(actor, page, null, model);
    }

    /** before: xem tin cũ hơn mốc này (link "Tải tin cũ hơn" khi tắt JavaScript); không có thì mở và đánh dấu đã đọc. */
    @GetMapping("/{id:\\d+}")
    public String open(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                       @RequestParam(required = false) Long before, @RequestParam(defaultValue = "0") int page,
                       Model model) {
        ChatView chat = before == null ? messageService.open(actor, id) : messageService.older(actor, id, before);
        model.addAttribute("form", new MessageForm());
        model.addAttribute("viewingOlder", before != null);
        return view(actor, page, chat, model);
    }

    @PostMapping("/{id:\\d+}/send")
    public String send(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                       @Valid @ModelAttribute("form") MessageForm form, BindingResult bindingResult, Model model) {
        if (!bindingResult.hasErrors()) {
            try {
                messageService.send(actor, id, form.getContent());
                return "redirect:" + BASE + "/" + id;
            } catch (BusinessException e) {
                FormErrors.apply(bindingResult, e);
            }
        }
        return view(actor, 0, messageService.open(actor, id), model);
    }

    @PostMapping("/{id:\\d+}/messages/{messageId:\\d+}/delete")
    public String delete(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                         @PathVariable Long messageId, HttpServletRequest request,
                         RedirectAttributes redirectAttributes) {
        messageService.delete(actor, id, messageId, request.getRemoteAddr());
        redirectAttributes.addFlashAttribute("successMessage", "Đã xóa tin nhắn");
        return "redirect:" + BASE + "/" + id;
    }

    /** Mảnh làm mới tự động: các tin mới hơn after (rỗng nếu không có). */
    @GetMapping("/{id:\\d+}/newer")
    public String newer(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                        @RequestParam(defaultValue = "0") long after, Model model) {
        model.addAttribute("messages", messageService.newer(actor, id, after));
        model.addAttribute("conversationId", id);
        return FRAGMENTS + " :: items";
    }

    /** Mảnh "tải tin cũ hơn": link tải tiếp (nếu còn) và các tin cũ hơn before. */
    @GetMapping("/{id:\\d+}/older")
    public String older(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                        @RequestParam Long before, Model model) {
        model.addAttribute("chat", messageService.older(actor, id, before));
        return FRAGMENTS + " :: older";
    }

    @GetMapping("/contacts")
    public String contacts(@AuthenticationPrincipal AppUserPrincipal actor,
                           @RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page,
                           Model model) {
        Page<ContactView> contacts = messageService.contacts(actor, q, page);
        model.addAttribute("contacts", contacts);
        model.addAttribute("q", q);
        model.addAttribute("pager", PageUtils.pager(contacts, BASE + "/contacts", PageUtils.params("q", q)));
        model.addAttribute("activeMenu", "messages");
        return "messages/contacts";
    }

    /** Nhắn cho một người từ danh bạ: đã có hội thoại thì chuyển tới đó; người không được nhắn → 403. */
    @GetMapping("/with/{userId:\\d+}")
    public String startWith(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long userId,
                            Model model) {
        ChatView chat = messageService.startWith(actor, userId);
        if (chat.conversationId() != null) {
            return "redirect:" + BASE + "/" + chat.conversationId();
        }
        model.addAttribute("form", new MessageForm());
        return view(actor, 0, chat, model);
    }

    @PostMapping("/with/{userId:\\d+}")
    public String sendTo(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long userId,
                         @Valid @ModelAttribute("form") MessageForm form, BindingResult bindingResult, Model model) {
        if (!bindingResult.hasErrors()) {
            try {
                Long conversationId = messageService.sendTo(actor, userId, form.getContent());
                return "redirect:" + BASE + "/" + conversationId;
            } catch (BusinessException e) {
                FormErrors.apply(bindingResult, e);
            }
        }
        return view(actor, 0, messageService.startWith(actor, userId), model);
    }

    private String view(AppUserPrincipal actor, int page, ChatView chat, Model model) {
        Page<ConversationListItem> conversations = messageService.conversations(actor, page);
        model.addAttribute("conversations", conversations);
        String path = chat == null || chat.conversationId() == null ? BASE : BASE + "/" + chat.conversationId();
        model.addAttribute("pager", PageUtils.pager(conversations, path, PageUtils.params()));
        model.addAttribute("chat", chat);
        model.addAttribute("activeMenu", "messages");
        return VIEW;
    }
}
