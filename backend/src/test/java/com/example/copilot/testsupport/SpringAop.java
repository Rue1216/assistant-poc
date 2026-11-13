package com.example.copilot.testsupport;

import org.springframework.context.ApplicationContext;

/**
 * Gives a hand-built bean the same proxy the container would: every advisor of the context (transaction
 * interceptor, aspects) in the container's order. Lets a test swap a collaborator or the clock and still go
 * through {@code @Transactional} and the use-case aspects.
 */
public final class SpringAop {

    private SpringAop() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T advised(ApplicationContext context, T bean) {
        return (T) context.getAutowireCapableBeanFactory()
                .initializeBean(bean, bean.getClass().getName() + "#handBuilt");
    }
}
