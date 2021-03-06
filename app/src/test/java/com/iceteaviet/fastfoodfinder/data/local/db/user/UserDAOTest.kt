package com.iceteaviet.fastfoodfinder.data.local.db.user

import org.junit.Before
import org.mockito.MockitoAnnotations

/**
 * Created by tom on 2019-05-29.
 */
class UserDAOTest {
    private var userDAO: UserDAO? = null

    @Before
    fun setupPreferencesHelper() {
        // Mockito has a very convenient way to inject mocks by using the @Mock annotation. To
        // inject the mocks in the test the initMocks method needs to be called.
        MockitoAnnotations.initMocks(this)

        // Get a reference to the class under test
        userDAO = UserDAO()
    }
}