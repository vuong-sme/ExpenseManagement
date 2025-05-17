package com.vti.service;

import com.vti.entity.User;
import com.vti.form.CreateUserForm;
import com.vti.jwtutils.CustomUserDetails;
import com.vti.jwtutils.TokenManager;
import com.vti.repository.IUserRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class UserService implements IUserService{

    @Autowired
    private IUserRepository userRepository;
    @Autowired
    private ModelMapper modelMapper;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    TokenManager tokenManager;

    @Override
    public User  createUser(CreateUserForm createUserForm) {
        if ((userRepository.findByEmail(createUserForm.getEmail())) != null) {
            throw new RuntimeException("Email đã tồn tại!");
        }

        User user = new User();
        user.setFullName(createUserForm.getFullName());
        user.setPassword(passwordEncoder.encode(createUserForm.getPassword()));
        user.setEmail(createUserForm.getEmail());

        User userNew = userRepository.save(user);

        return userNew;
        }

    @Override
    public boolean isUserExistsByID(Integer id) {
        return userRepository.existsById(id);
    }

    @Override
    public boolean isUserExistsByPhoneNumber(String phoneNumber) {
        return userRepository.existsByPhoneNumber(phoneNumber);
    }

    @Override
    public User findUserByPhoneNumber(String phoneNumber) {
        return userRepository.findByPhoneNumber(phoneNumber);
    }

    @Override
    public User findUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }
}
