package com.collabtech.platform.shared.application.cqrs;

import com.collabtech.platform.shared.application.cqrs.Command;

public interface CommandHandler<C extends Command<R>, R> {
    R handle(C command);
}
