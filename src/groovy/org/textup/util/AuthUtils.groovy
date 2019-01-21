package org.textup.util

import grails.compiler.GrailsTypeChecked
import grails.plugin.springsecurity.userdetails.NoStackUsernameNotFoundException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.userdetails.UserDetails

@GrailsTypeChecked
class AuthUtils {

    static Result<Staff> tryGetAuthUser() {
        AuthUtils.tryGetAuthId()
            .then { Long authId ->
                Staff s1 = Staff.get(authId)
                isActive(s1) ? IOCUtils.resultFactory.success(s1) : notAllowed()
            }
    }

    static Result<Long> tryGetAuthId() {
        IOCUtils.security.isLoggedIn() ?
            IOCUtils.resultFactory.success(IOCUtils.security.loadCurrentUser() as Long) :
            notAllowed()
    }

    static Result<Void> isAllowed(boolean outcome) {
        outcome ? Result.void() : notAllowed()
    }

    // see: http://blog.cwill-dev.com/2011/05/11/grails-springsecurityservice-authenticate-via-code-manually/
    static boolean isValidCredentials(String username, String password) {
        try {
            UserDetails details = IOCUtils.security
                .userDetailsService
                .loadUserByUsername(username)
            IOCUtils.getBean(DaoAuthenticationProvider)
                .authenticate(new UsernamePasswordAuthenticationToken(details, password))
                .authenticated
        }
        catch (NoStackUsernameNotFoundException | BadCredentialsException e) {
            return false
        }
    }

    static String encodeSecureString(String val) {
        IOCUtils.security
            .passwordEncoder
            .encodePassword(val, null)
    }

    static boolean isSecureStringValid(String reference, String val) {
        IOCUtils.security
            .passwordEncoder
            .isPasswordValid(reference, val, null)
    }

    // Helpers
    // -------

    protected static boolean isActive(Staff s1) {
        StaffStatus.ACTIVE_STATUSES.contains(s1?.status) &&
        OrgStatus.ACTIVE_STATUSES.contains(s1?.org?.status)
    }

    protected static Result<?> notAllowed() {
        IOCUtils.resultFactory.failWithCodeAndStatus("authUtils.notAllowed") // TODO
    }
}
