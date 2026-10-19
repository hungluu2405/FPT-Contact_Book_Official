package vn.edu.fpt.contactbook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Form gửi tin nhắn.
 */
@Getter
@Setter
@NoArgsConstructor
public class MessageForm {

    @NotBlank(message = "{message.content.required}")
    @Size(max = 2000, message = "{message.content.size}")
    private String content;
}
