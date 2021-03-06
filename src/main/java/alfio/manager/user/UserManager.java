/**
 * This file is part of alf.io.
 *
 * alf.io is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * alf.io is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with alf.io.  If not, see <http://www.gnu.org/licenses/>.
 */
package alfio.manager.user;

import alfio.model.user.Authority;
import alfio.model.user.Organization;
import alfio.model.user.User;
import alfio.model.user.UserWithPassword;
import alfio.repository.user.AuthorityRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.repository.user.UserRepository;
import alfio.repository.user.join.UserOrganizationRepository;
import alfio.util.PasswordGenerator;
import alfio.util.ValidationResult;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static java.util.stream.Collectors.toList;

@Component
public class UserManager {

    private static final Function<Integer, Integer> ID_EVALUATOR = id -> Optional.ofNullable(id).orElse(Integer.MIN_VALUE);
    private final AuthorityRepository authorityRepository;
    private final OrganizationRepository organizationRepository;
    private final UserOrganizationRepository userOrganizationRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public UserManager(AuthorityRepository authorityRepository,
                       OrganizationRepository organizationRepository,
                       UserOrganizationRepository userOrganizationRepository,
                       UserRepository userRepository,
                       PasswordEncoder passwordEncoder) {
        this.authorityRepository = authorityRepository;
        this.organizationRepository = organizationRepository;
        this.userOrganizationRepository = userOrganizationRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<Authority> getUserAuthorities(User user) {
        return authorityRepository.findGrantedAuthorities(user.getUsername());
    }

    public List<User> findAllUsers(String username) {
        return findUserOrganizations(username)
                .stream()
                .flatMap(o -> userOrganizationRepository.findByOrganizationId(o.getId()).stream())
                .map(uo -> userRepository.findById(uo.getUserId()))
                .filter(User::isEnabled)
                .collect(toList());
    }

    public User findUser(int id) {
        return userRepository.findById(id);
    }

    public List<Organization> findUserOrganizations(String username) {
        return findUserOrganizations(userRepository.getByUsername(username));
    }

    public Organization findOrganizationById(int id, String username) {
        return findUserOrganizations(username)
                .stream()
                .filter(o -> o.getId() == id)
                .findFirst()
                .orElseThrow(IllegalArgumentException::new);
    }

    public List<Organization> findUserOrganizations(User user) {
        if (isAdmin(user)) {
            return organizationRepository.findAll();
        }
        return userOrganizationRepository.findByUserId(user.getId())
                .stream()
                .map(uo -> organizationRepository.getById(uo.getOrganizationId()))
                .collect(toList());
    }

    public boolean isAdmin(User user) {
        return getUserAuthorities(user).stream().anyMatch(a -> a.getRole().equals(AuthorityRepository.ROLE_ADMIN));
    }

    public List<User> findMembers(Organization organization) {
        return userOrganizationRepository.findByOrganizationId(organization.getId())
                .stream()
                .map(uo -> userRepository.findById(uo.getUserId()))
                .collect(toList());
    }

    public void createOrganization(String name, String description, String email) {
        organizationRepository.create(name, description, email);
    }

    public ValidationResult validateOrganization(Integer id, String name, String email, String description) {
        int orgId = ID_EVALUATOR.apply(id);
        final long existing = organizationRepository.findByName(name)
                .stream()
                .filter(o -> o.getId() != orgId)
                .count();
        if(existing > 0) {
            return ValidationResult.failed(new ValidationResult.ValidationError("name", "There is already another organization with the same name."));
        }
        Validate.notBlank(name, "name can't be empty");
        Validate.notBlank(email, "email can't be empty");
        Validate.notBlank(description, "description can't be empty");
        return ValidationResult.success();
    }

    @Transactional
    public void editUser(int id, int organizationId, String username, String firstName, String lastName, String emailAddress) {
        int userOrganizationResult = userOrganizationRepository.updateUserOrganization(id, organizationId);
        Assert.isTrue(userOrganizationResult == 1, "unexpected error during organization update");
        int userResult = userRepository.update(id, username, firstName, lastName, emailAddress);
        Assert.isTrue(userResult == 1, "unexpected error during user update");
    }

    @Transactional
    public UserWithPassword insertUser(int organizationId, String username, String firstName, String lastName, String emailAddress) {
        Organization organization = organizationRepository.getById(organizationId);
        String userPassword = PasswordGenerator.generateRandomPassword();
        Pair<Integer, Integer> result = userRepository.create(username, passwordEncoder.encode(userPassword), firstName, lastName, emailAddress, true);
        userOrganizationRepository.create(result.getValue(), organization.getId());
        authorityRepository.create(username, AuthorityRepository.ROLE_OPERATOR);
        return new UserWithPassword(userRepository.findById(result.getValue()), userPassword, UUID.randomUUID().toString());
    }

    @Transactional
    public UserWithPassword resetPassword(int userId) {
        User user = findUser(userId);
        String password = PasswordGenerator.generateRandomPassword();
        Validate.isTrue(userRepository.resetPassword(userId, passwordEncoder.encode(password)) == 1, "error during password reset");
        return new UserWithPassword(user, password, UUID.randomUUID().toString());
    }

    @Transactional
    public void deleteUser(int userId, String currentUsername) {
        User currentUser = userRepository.findEnabledByUsername(currentUsername);
        Assert.isTrue(userId != currentUser.getId(), "sorry but you cannot commit suicide");
        Assert.isTrue(userRepository.toggleEnabled(userId, false) == 1, "unexpected update result");
    }

    public ValidationResult validateUser(Integer id, String username, int organizationId, String firstName, String lastName, String emailAddress) {
        int userId = ID_EVALUATOR.apply(id);
        final long existing = userRepository.findByUsername(username)
                .stream()
                .filter(u -> u.getId() != userId)
                .count();
        if(existing > 0) {
            return ValidationResult.failed(new ValidationResult.ValidationError("username", "There is already another user with the same username."));
        }
        return ValidationResult.success();
    }


}
