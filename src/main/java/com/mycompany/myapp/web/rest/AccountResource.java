package com.mycompany.myapp.web.rest;


import com.mycompany.myapp.domain.User;
import com.mycompany.myapp.repository.UserRepository;
import com.mycompany.myapp.repository.search.UserSearchRepository;
import com.mycompany.myapp.security.SecurityUtils;
import com.mycompany.myapp.service.DoctorService;
import com.mycompany.myapp.service.MailService;
import com.mycompany.myapp.service.PatientService;
import com.mycompany.myapp.service.UserService;
import com.mycompany.myapp.service.dto.DoctorDTO;
import com.mycompany.myapp.service.dto.PasswordChangeDTO;
import com.mycompany.myapp.service.dto.PatientDTO;
import com.mycompany.myapp.service.dto.UserDTO;
import com.mycompany.myapp.web.rest.errors.*;
import com.mycompany.myapp.web.rest.vm.KeyAndPasswordVM;
import com.mycompany.myapp.web.rest.vm.ManagedUserVM;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.math.BigDecimal;
import java.util.*;

/**
 * REST controller for managing the current user's account.
 */
@RestController
@RequestMapping("/api")
public class AccountResource {

    private final Logger log = LoggerFactory.getLogger(AccountResource.class);

    private final UserRepository userRepository;

    private final UserService userService;

    private final UserSearchRepository userSearchRepository;

    private final MailService mailService;

    private final PatientService patientService;

    private final DoctorService doctorService;

    public AccountResource(UserRepository userRepository, UserService userService, MailService mailService,UserSearchRepository userSearchRepository,PatientService patientService,DoctorService doctorService) {

        this.userRepository = userRepository;
        this.userService = userService;
        this.mailService = mailService;
        this.userSearchRepository=userSearchRepository;
        this.patientService=patientService;
        this.doctorService=doctorService;
    }

    /**
     * POST  /register : register the user.
     *
     * @param managedUserVM the managed user View Model
     * @throws InvalidPasswordException 400 (Bad Request) if the password is incorrect
     * @throws EmailAlreadyUsedException 400 (Bad Request) if the email is already used
     * @throws LoginAlreadyUsedException 400 (Bad Request) if the login is already used
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void registerAccount(@Valid @RequestBody ManagedUserVM managedUserVM) {
        if (!checkPasswordLength(managedUserVM.getPassword())) {
            throw new InvalidPasswordException();
        }
        System.out.println("elaff"+managedUserVM.getAuthorities().iterator().next());
        User user = userService.registerUser(managedUserVM, managedUserVM.getPassword());
        //log.debug("Activating user for activation key {}", key);
        userRepository.findOneByLogin(user.getLogin())
            .map(user1 -> {
                // activate given user for the registration key.
                user1.setActivated(true);
                user1.setActivationKey(null);
                userSearchRepository.save(user1);
                log.debug("Activated user: {}", user1);
                userRepository.save(user1);
                return user1;
            });
        if(managedUserVM.getAuthorities().iterator().next().equals("ROLE_PATIENT"))
        {      System.out.println("hereo");
            PatientDTO patient = new PatientDTO();
            patient.setCin(user.getId());
            patient.setName(user.getLogin());
            patient.setEmail(user.getEmail());
            patient.setPhoneNumber(216000000l);
            patientService.save(patient);
        }
        if(managedUserVM.getAuthorities().iterator().next().equals("ROLE_Doctor"))
        {      System.out.println("hereo2");
            DoctorDTO doctor = new DoctorDTO() ;
            BigDecimal a = new BigDecimal("2160000");
            BigDecimal b = new BigDecimal(user.getId());

            doctor.setName(user.getLogin());
            doctor.setEmail(user.getEmail());
            doctor.setPhoneNumber(a);
            doctor.setCin(b);
            doctor.setAddress("please provide your adress ");
            doctor.setSpeciality("please provide your speciality");
            doctorService.save(doctor);
        }

        mailService.sendActivationEmail(user);
    }



    /**
     * GET  /activate : activate the registered user.
     *
     * @param key the activation key
     * @throws RuntimeException 500 (Internal Server Error) if the user couldn't be activated
     */
    @GetMapping("/activate")
    public void activateAccount(@RequestParam(value = "key") String key) {
        Optional<User> user = userService.activateRegistration(key);
        if (!user.isPresent()) {
            throw new InternalServerErrorException("No user was found for this activation key");
        }
    }

    /**
     * GET  /authenticate : check if the user is authenticated, and return its login.
     *
     * @param request the HTTP request
     * @return the login if the user is authenticated
     */
    @GetMapping("/authenticate")
    public String isAuthenticated(HttpServletRequest request) {
        log.debug("REST request to check if the current user is authenticated");
        return request.getRemoteUser();
    }

    /**
     * GET  /account : get the current user.
     *
     * @return the current user
     * @throws RuntimeException 500 (Internal Server Error) if the user couldn't be returned
     */
    @GetMapping("/account")
    public UserDTO getAccount() {
        return userService.getUserWithAuthorities()
            .map(UserDTO::new)
            .orElseThrow(() -> new InternalServerErrorException("User could not be found"));
    }

    /**
     * POST  /account : update the current user information.
     *
     * @param userDTO the current user information
     * @throws EmailAlreadyUsedException 400 (Bad Request) if the email is already used
     * @throws RuntimeException 500 (Internal Server Error) if the user login wasn't found
     */
    @PostMapping("/account")
    public void saveAccount(@Valid @RequestBody UserDTO userDTO) {
        String userLogin = SecurityUtils.getCurrentUserLogin().orElseThrow(() -> new InternalServerErrorException("Current user login not found"));
        Optional<User> existingUser = userRepository.findOneByEmailIgnoreCase(userDTO.getEmail());
        if (existingUser.isPresent() && (!existingUser.get().getLogin().equalsIgnoreCase(userLogin))) {
            throw new EmailAlreadyUsedException();
        }
        Optional<User> user = userRepository.findOneByLogin(userLogin);
        if (!user.isPresent()) {
            throw new InternalServerErrorException("User could not be found");
        }
        userService.updateUser(userDTO.getFirstName(), userDTO.getLastName(), userDTO.getEmail(),
            userDTO.getLangKey(), userDTO.getImageUrl());
    }

    /**
     * POST  /account/change-password : changes the current user's password
     *
     * @param passwordChangeDto current and new password
     * @throws InvalidPasswordException 400 (Bad Request) if the new password is incorrect
     */
    @PostMapping(path = "/account/change-password")
    public void changePassword(@RequestBody PasswordChangeDTO passwordChangeDto) {
        if (!checkPasswordLength(passwordChangeDto.getNewPassword())) {
            throw new InvalidPasswordException();
        }
        userService.changePassword(passwordChangeDto.getCurrentPassword(), passwordChangeDto.getNewPassword());
    }

    /**
     * POST   /account/reset-password/init : Send an email to reset the password of the user
     *
     * @param mail the mail of the user
     * @throws EmailNotFoundException 400 (Bad Request) if the email address is not registered
     */
    @PostMapping(path = "/account/reset-password/init")
    public void requestPasswordReset(@RequestBody String mail) {
       mailService.sendPasswordResetMail(
           userService.requestPasswordReset(mail)
               .orElseThrow(EmailNotFoundException::new)
       );
    }

    /**
     * POST   /account/reset-password/finish : Finish to reset the password of the user
     *
     * @param keyAndPassword the generated key and the new password
     * @throws InvalidPasswordException 400 (Bad Request) if the password is incorrect
     * @throws RuntimeException 500 (Internal Server Error) if the password could not be reset
     */
    @PostMapping(path = "/account/reset-password/finish")
    public void finishPasswordReset(@RequestBody KeyAndPasswordVM keyAndPassword) {
        if (!checkPasswordLength(keyAndPassword.getNewPassword())) {
            throw new InvalidPasswordException();
        }
        Optional<User> user =
            userService.completePasswordReset(keyAndPassword.getNewPassword(), keyAndPassword.getKey());

        if (!user.isPresent()) {
            throw new InternalServerErrorException("No user was found for this reset key");
        }
    }

    private static boolean checkPasswordLength(String password) {
        return !StringUtils.isEmpty(password) &&
            password.length() >= ManagedUserVM.PASSWORD_MIN_LENGTH &&
            password.length() <= ManagedUserVM.PASSWORD_MAX_LENGTH;
    }
}
