package com.library.seat.aop;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LogOperation {
    String module() default "";
    String action() default "";
    String detail() default "";
}
