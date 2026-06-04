package com.jobtracker.unit.mcp;

import com.jobtracker.dto.auth.UserResponse;
import com.jobtracker.entity.User;
import com.jobtracker.mapper.AuthMapper;
import com.jobtracker.mcp.tools.McpProfileTools;
import com.jobtracker.util.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpProfileToolsTest {

    @Mock
    private AuthMapper authMapper;

    @Mock
    private SecurityUtils securityUtils;

    @InjectMocks
    private McpProfileTools tools;

    @Test
    void getCurrentUser_mapsAuthenticatedUser() {
        User user = mock(User.class);
        UserResponse expected = mock(UserResponse.class);
        when(securityUtils.getCurrentUser()).thenReturn(user);
        when(authMapper.toUserResponse(user)).thenReturn(expected);

        UserResponse result = tools.getCurrentUser();

        assertThat(result).isSameAs(expected);
        verify(securityUtils).getCurrentUser();
        verify(authMapper).toUserResponse(user);
    }
}
