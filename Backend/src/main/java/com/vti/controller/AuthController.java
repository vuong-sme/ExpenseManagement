package com.vti.controller;

import com.vti.dto.ErrorResponse;
import com.vti.entity.PasswordResetToken;
import com.vti.entity.User;
import com.vti.form.CreateUserForm;
import com.vti.jwtutils.CustomUserDetails;
import com.vti.jwtutils.JwtUserDetailsService;
import com.vti.jwtutils.TokenManager;
import com.vti.mail.GenericResponse;
import com.vti.mail.ISendMailService;
import com.vti.models.JwtRequestModel;
import com.vti.service.IPasswordResetToken;
import com.vti.service.IUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.expression.AccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;

import static com.vti.entity.PasswordResetToken.EXPIRATION;

@RestController
@RequestMapping("api/v1/auth")
public class AuthController {

    @Autowired
    private JwtUserDetailsService jwtUserDetailsService;
    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private TokenManager tokenManager;
    @Autowired
    private IUserService userService;
    @Autowired
    private IPasswordResetToken passwordResetToken;
    @Autowired
    private ISendMailService sendMailService;
    @Autowired
    private JavaMailSender mailSender;
    @Qualifier("messageSource")
    @Autowired
    private MessageSource messages;

    @PostMapping("/login")
    public ResponseEntity<?> createToken(@RequestBody JwtRequestModel request) {
        User user = userService.findUserByPhoneNumber(request.getPhoneNumber());

        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("Account does not exist"));
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getPhoneNumber(), request.getPassword()));
        } catch (DisabledException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse("User is disabled"));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid username or password"));
        }

        final CustomUserDetails userDetails = jwtUserDetailsService.loadUserByUsername(request.getPhoneNumber());
        final String jwtToken = tokenManager.generateToken(userDetails);
        return ResponseEntity.ok(Map.of(
                "token", jwtToken,
                "user", Map.of(
                        "id", userDetails.getUserId(),
                        "fullName", userDetails.getFullName()
                )
        ));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody CreateUserForm createUserForm) {
        try {
            User user = userService.createUser(createUserForm);
            return user!= null ?
                    ResponseEntity.status(200).body("Register success") :
                    ResponseEntity.notFound().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }


    @PostMapping("/forgot-password")
    public GenericResponse resetPassword(HttpServletRequest request, @RequestParam("email") String userEmail) throws AccessException {
        User user = userService.findUserByEmail(userEmail);
        if (user == null) {
            throw new AccessException("No user found with email: " + userEmail);
        }

        // Tạo token mới và thiết lập thời gian hết hạn
        PasswordResetToken token = new PasswordResetToken();
        token.setToken(UUID.randomUUID().toString());
        token.setUser(user);
        Timestamp now = new Timestamp(System.currentTimeMillis());
        Timestamp expiryDate = new Timestamp(now.getTime() + (EXPIRATION * 60 * 1000)); // 24 giờ
        token.setExpDate(expiryDate);
        token.setCreatedAt(now);

        // Lưu token vào cơ sở dữ liệu
        passwordResetToken.createNewPasswordToken(token);

        // Gửi email chứa token reset mật khẩu
        SimpleMailMessage mail = sendMailService.constructResetTokenEmail("http://localhost:3000", request.getLocale(),
                token.getToken(), user, "/reset-password?token=", "Vui lòng click vào đường link dưới đây để khôi phục mật khẩu", "Reset Password");
        mailSender.send(mail);

        // Trả về thông báo thành công
        String message = messages.getMessage("message.resetPassword", null, "Please check your email to reset password!",
                request.getLocale());
        return new GenericResponse(message);
    }
}
