package com.example.copilot.aiassistant.application.port;

import com.example.copilot.aiassistant.domain.user.UserContext;


public interface CurrentUserPort {

    UserContext currentUser();
}
