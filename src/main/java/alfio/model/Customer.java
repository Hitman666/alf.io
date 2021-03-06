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
package alfio.model;

import alfio.datamapper.ConstructorAnnotationRowMapper.Column;
import lombok.Getter;

@Getter
public class Customer {
    private final int id;
    private final String username;
    private final String firstName;
    private final String lastName;
    private final String address;
    private final String zip;
    private final String city;
    private final String state;
    private final String country;
    private final String emailAddress;

    public Customer(@Column("id") int id, @Column("username") String username, @Column("first_name") String firstName,
                    @Column("last_name") String lastName, @Column("address") String address, @Column("zip") String zip,
                    @Column("city") String city, @Column("state") String state, @Column("country") String country,
                    @Column("email_address") String emailAddress) {
        this.id = id;
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
        this.address = address;
        this.zip = zip;
        this.city = city;
        this.state = state;
        this.country = country;
        this.emailAddress = emailAddress;
    }
}
