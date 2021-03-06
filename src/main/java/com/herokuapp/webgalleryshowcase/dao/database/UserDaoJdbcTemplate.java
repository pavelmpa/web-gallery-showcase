package com.herokuapp.webgalleryshowcase.dao.database;

import com.herokuapp.webgalleryshowcase.dao.UserDao;
import com.herokuapp.webgalleryshowcase.dao.database.exceptions.EmailAlreadyTakenException;
import com.herokuapp.webgalleryshowcase.dao.database.exceptions.UserNotFoundException;
import com.herokuapp.webgalleryshowcase.dao.database.exceptions.UsernameAlreadyTakenException;
import com.herokuapp.webgalleryshowcase.domain.User;
import com.herokuapp.webgalleryshowcase.domain.UserAuthority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;

@Repository
public class UserDaoJdbcTemplate implements UserDao {

    private final Logger log = LoggerFactory.getLogger(UserDaoJdbcTemplate.class);

    private static final String ADD_USER = "INSERT INTO web_gallery.users (first_name, last_name, username, email, password) " +
            "VALUES (:firstName, :lastName, :username, :email, :password)";

    private static final String CHECK_USERNAME = "SELECT COUNT(*) FROM web_gallery.users WHERE username = :username";

    private static final String CHECK_EMAIL = "SELECT COUNT(*) FROM web_gallery.users WHERE email = :email";

    private static final String GET_USER_BY_EMAIL =
            "SELECT web_gallery.users.id, first_name, last_name, email, username, password, enabled, join_timestamp, authority " +
                    "FROM web_gallery.users " +
                    "INNER JOIN web_gallery.user_authorities ON web_gallery.users.authorities = web_gallery.user_authorities.id " +
                    "WHERE email = :email";

    private NamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    public void setDataSource(DataSource dataSource) {
        jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
    }

    @Override
    public void addUser(User user) {
        if (isEmailTaken(user.getEmail())) {
            throw new EmailAlreadyTakenException("This email already taken.");
        }

        if (isUsernameTaken(user.getUsername())) {
            throw new UsernameAlreadyTakenException("This username already taken.");
        }

        SqlParameterSource params = new BeanPropertySqlParameterSource(user);
        jdbcTemplate.update(ADD_USER, params);
    }

    @Override
    public User getUserByEmail(String email) {
        User user;
        try {
            SqlParameterSource paramSource = new MapSqlParameterSource("email", email);
            user = jdbcTemplate.queryForObject(GET_USER_BY_EMAIL, paramSource, userRowMapper);
        } catch (EmptyResultDataAccessException ex) {
            log.info("Requested user not found." + ex.getMessage());
            throw new UserNotFoundException(1, ex);
        }
        return user;
    }

    private boolean isUsernameTaken(String username) {
        SqlParameterSource paramSource = new MapSqlParameterSource("username", username);
        return jdbcTemplate.queryForObject(CHECK_USERNAME, paramSource, Integer.class) > 0;
    }

    private boolean isEmailTaken(String email) {
        SqlParameterSource paramSource = new MapSqlParameterSource("email", email);
        return jdbcTemplate.queryForObject(CHECK_EMAIL, paramSource, Integer.class) > 0;
    }

    private final RowMapper<User> userRowMapper = new RowMapper<User>() {

        @Override
        public User mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            User user = new User();

            user.setId(resultSet.getInt("id"));
            user.setFirstName(resultSet.getString("first_name"));
            user.setLastName(resultSet.getString("last_name"));
            user.setEmail(resultSet.getString("email"));
            user.setUsername(resultSet.getString("username"));
            user.setPassword(resultSet.getString("password"));
            user.setJoinDate(resultSet.getDate("join_timestamp"));
            user.setEnabled(resultSet.getBoolean("enabled"));

            UserAuthority userAuthority = new UserAuthority(resultSet.getString("authority"));
            Collection<UserAuthority> authorities = new ArrayList<>();
            authorities.add(userAuthority);
            user.setAuthorities(authorities);

            return user;
        }
    };
}