package com.mycompany.myapp.web.rest;

import com.mycompany.myapp.config.Constants;
import com.mycompany.myapp.domain.*;
import com.mycompany.myapp.repository.DoctorRepository;
import com.mycompany.myapp.repository.PatientRepository;
import com.mycompany.myapp.repository.RequestRepository;
import com.mycompany.myapp.repository.UserRepository;
import com.mycompany.myapp.repository.search.UserSearchRepository;
import com.mycompany.myapp.security.AuthoritiesConstants;
import com.mycompany.myapp.security.SecurityUtils;
import com.mycompany.myapp.service.*;
import com.mycompany.myapp.service.dto.UserDTO;
import com.mycompany.myapp.web.rest.errors.BadRequestAlertException;
import com.mycompany.myapp.web.rest.errors.EmailAlreadyUsedException;
import com.mycompany.myapp.web.rest.errors.LoginAlreadyUsedException;
import com.mycompany.myapp.web.rest.util.HeaderUtil;
import com.mycompany.myapp.web.rest.util.PaginationUtil;
import io.github.jhipster.web.util.ResponseUtil;

import io.micrometer.core.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.mycompany.myapp.domain.Request_.patient;
import static org.elasticsearch.index.query.QueryBuilders.*;

/**
 * REST controller for managing users.
 * <p>
 * This class accesses the User entity, and needs to fetch its collection of authorities.
 * <p>
 * For a normal use-case, it would be better to have an eager relationship between User and Authority,
 * and send everything to the client side: there would be no View Model and DTO, a lot less code, and an outer-join
 * which would be good for performance.
 * <p>
 * We use a View Model and a DTO for 3 reasons:
 * <ul>
 * <li>We want to keep a lazy association between the user and the authorities, because people will
 * quite often do relationships with the user, and we don't want them to get the authorities all
 * the time for nothing (for performance reasons). This is the #1 goal: we should not impact our users'
 * application because of this use-case.</li>
 * <li> Not having an outer join causes n+1 requests to the database. This is not a real issue as
 * we have by default a second-level cache. This means on the first HTTP call we do the n+1 requests,
 * but then all authorities come from the cache, so in fact it's much better than doing an outer join
 * (which will get lots of data from the database, for each HTTP call).</li>
 * <li> As this manages users, for security reasons, we'd rather have a DTO layer.</li>
 * </ul>
 * <p>
 * Another option would be to have a specific JPA entity graph to handle this case.
 */
@RestController
@RequestMapping("/api")
public class UserResource {

    private final Logger log = LoggerFactory.getLogger(UserResource.class);

    private final UserService userService;

    private final UserRepository userRepository;

    private final MailService mailService;



    private final RequestService requestService;

    private final DoctorRepository doctorRepository;

    private final AppointmentService appointmentService;

    private final PatientRepository patientRepository;

    private final RequestRepository requestRepository;






    private final UserSearchRepository userSearchRepository;

    public UserResource(UserService userService, UserRepository userRepository, MailService mailService, RequestService requestService, DoctorRepository doctorRepository, AppointmentService appointmentService, PatientRepository patientRepository, RequestRepository requestRepository, UserSearchRepository userSearchRepository) {

        this.userService = userService;
        this.userRepository = userRepository;
        this.mailService = mailService;

        this.requestService = requestService;
        this.doctorRepository = doctorRepository;
        this.appointmentService = appointmentService;
        this.patientRepository = patientRepository;
        this.requestRepository = requestRepository;
        this.userSearchRepository = userSearchRepository;
    }

    /**
     * POST  /users  : Creates a new user.
     * <p>
     * Creates a new user if the login and email are not already used, and sends an
     * mail with an activation link.
     * The user needs to be activated on creation.
     *
     * @param userDTO the user to create
     * @return the ResponseEntity with status 201 (Created) and with body the new user, or with status 400 (Bad Request) if the login or email is already in use
     * @throws URISyntaxException if the Location URI syntax is incorrect
     * @throws BadRequestAlertException 400 (Bad Request) if the login or email is already in use
     */
    @PostMapping("/users")
    @PreAuthorize("hasRole(\"" + AuthoritiesConstants.ADMIN + "\")")
    public ResponseEntity<User> createUser(@Valid @RequestBody UserDTO userDTO) throws URISyntaxException {
        log.debug("REST request to save User : {}", userDTO);

        if (userDTO.getId() != null) {
            throw new BadRequestAlertException("A new user cannot already have an ID", "userManagement", "idexists");
            // Lowercase the user login before comparing with database
        } else if (userRepository.findOneByLogin(userDTO.getLogin().toLowerCase()).isPresent()) {
            throw new LoginAlreadyUsedException();
        } else if (userRepository.findOneByEmailIgnoreCase(userDTO.getEmail()).isPresent()) {
            throw new EmailAlreadyUsedException();
        } else {
            User newUser = userService.createUser(userDTO);

            mailService.sendCreationEmail(newUser);
            return ResponseEntity.created(new URI("/api/users/" + newUser.getLogin()))
                .headers(HeaderUtil.createAlert( "userManagement.created", newUser.getLogin()))
                .body(newUser);
        }
    }


    /**
     * PUT /users : Updates an existing User.
     *
     * @param userDTO the user to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated user
     * @throws EmailAlreadyUsedException 400 (Bad Request) if the email is already in use
     * @throws LoginAlreadyUsedException 400 (Bad Request) if the login is already in use
     */
    @PutMapping("/users")
    @PreAuthorize("hasRole(\"" + AuthoritiesConstants.ADMIN + "\")")
    public ResponseEntity<UserDTO> updateUser(@Valid @RequestBody UserDTO userDTO) {
        log.debug("REST request to update User : {}", userDTO);
        Optional<User> existingUser = userRepository.findOneByEmailIgnoreCase(userDTO.getEmail());
        if (existingUser.isPresent() && (!existingUser.get().getId().equals(userDTO.getId()))) {
            throw new EmailAlreadyUsedException();
        }
        existingUser = userRepository.findOneByLogin(userDTO.getLogin().toLowerCase());
        if (existingUser.isPresent() && (!existingUser.get().getId().equals(userDTO.getId()))) {
            throw new LoginAlreadyUsedException();
        }
        Optional<UserDTO> updatedUser = userService.updateUser(userDTO);

        return ResponseUtil.wrapOrNotFound(updatedUser,
            HeaderUtil.createAlert("userManagement.updated", userDTO.getLogin()));
    }

    /**
     * GET /users : get all users.
     *
     * @param pageable the pagination information
     * @return the ResponseEntity with status 200 (OK) and with body all users
     */
    @GetMapping("/users")
    public ResponseEntity<List<UserDTO>> getAllUsers(Pageable pageable) {
        final Page<UserDTO> page = userService.getAllManagedUsers(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(page, "/api/users");
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    /**
     * @return a string list of the all of the roles
     */
    @GetMapping("/users/authorities")
    @PreAuthorize("hasRole(\"" + AuthoritiesConstants.ADMIN + "\")")
    public List<String> getAuthorities() {
        return userService.getAuthorities();
    }

    /**
     * GET /users/:login : get the "login" user.
     *
     * @param login the login of the user to find
     * @return the ResponseEntity with status 200 (OK) and with body the "login" user, or with status 404 (Not Found)
     */
    @GetMapping("/users/{login:" + Constants.LOGIN_REGEX + "}")
    public ResponseEntity<UserDTO> getUser(@PathVariable String login) {
        log.debug("REST request to get User : {}", login);
        return ResponseUtil.wrapOrNotFound(
            userService.getUserWithAuthoritiesByLogin(login)
                .map(UserDTO::new));
    }

    /**
     * DELETE /users/:login : delete the "login" User.
     *
     * @param login the login of the user to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/users/{login:" + Constants.LOGIN_REGEX + "}")
    @PreAuthorize("hasRole(\"" + AuthoritiesConstants.ADMIN + "\")")
    public ResponseEntity<Void> deleteUser(@PathVariable String login) {
        log.debug("REST request to delete User: {}", login);
        userService.deleteUser(login);
        return ResponseEntity.ok().headers(HeaderUtil.createAlert( "userManagement.deleted", login)).build();
    }

    /**
     * SEARCH /_search/users/:query : search for the User corresponding
     * to the query.
     *
     * @param query the query to search
     * @return the result of the search
     */
    @GetMapping("/_search/users/{query}")
    public List<User> search(@PathVariable String query) {
        return StreamSupport
            .stream(userSearchRepository.search(queryStringQuery(query)).spliterator(), false)
            .collect(Collectors.toList());
    }

    @GetMapping("/user/MyDoctors")
    @Timed
    public List<Doctor> getDoctors() {
        String userLogin = SecurityUtils.getCurrentUserLogin().get();
        User currentUser = userService.getUserWithAuthoritiesByLogin(userLogin).get();
        List<Request> requests= requestService.findAll();
        List<Doctor> result = new ArrayList<>();
        log.debug(currentUser.getLogin());

        for (Request request : requests ){
            log.debug(request.getPatient().getName() , "allll");
            if (request.getPatient().getCin()==currentUser.getId()){
                log.debug(request.getPatient().getName());
                Long id = request.getDoctor().getId();
                Doctor doctor = doctorRepository.findById(id).get();
                result.add(doctor);
            }
        }
        return result;
    }

    @GetMapping("/user/MyAppointments")
    @Timed
    public List<Appointment> getAppointments() {
        String userLogin = SecurityUtils.getCurrentUserLogin().get();
        User currentUser = userService.getUserWithAuthoritiesByLogin(userLogin).get();
        log.debug(currentUser.getAuthorities().iterator().next().getName()+" heyyyyyy !!!!!!!!!!!");
        if (currentUser.getAuthorities().iterator().next().getName().equals("ROLE_PATIENT")){
            List<Appointment> appointments= appointmentService.findAll();
            List<Appointment> result = new ArrayList<>();
            log.debug(currentUser.getLogin());

            for (Appointment appointment : appointments ){

                //Patient patient = patientRepository.findOneWithEagerRelationships(appointment.getRequest().getPatient().getId()).get();
                Patient patient = appointment.getRequest().getPatient();
                log.debug("here !!!");
                log.debug(patient.toString());


                if (patient.getCin()==currentUser.getId()){
                    result.add(appointment);
                }
            }
            return result;
        }
        else{
            return appointmentService.findAll();
        }
    }

    @GetMapping("/user/MyAppointmentsDoctor")
    @Timed
    public List<Doctor> getAppointmentDoctorName() {
        String userLogin = SecurityUtils.getCurrentUserLogin().get();
        User currentUser = userService.getUserWithAuthoritiesByLogin(userLogin).get();
        List<Appointment> appointments= appointmentService.findAll();
        List<Doctor> result = new ArrayList<>();
        //log.debug(currentUser.getLogin());

        for (Appointment appointment : appointments ){

            //Patient patient = patientRepository.findOneWithEagerRelationships(appointment.getRequest().getPatient().getId()).get();
            Patient patient = appointment.getRequest().getPatient();
            log.debug("here !!!");
            log.debug(patient.toString());


            if (patient.getCin()==currentUser.getId()){
                result.add(appointment.getRequest().getDoctor());
                log.debug("calling doctor details !!!!!!!");
                log.debug(appointment.getRequest().getDoctor().toString());

            }
        }
        return result;
    }

    @GetMapping("/user/getCurrentUser")
    public ResponseEntity<User> getCurrentUser() {
        User user=userRepository.findOneByLogin(SecurityUtils.getCurrentUserLogin().get()).get();
        return ResponseEntity.ok().body(user);
    }


}
