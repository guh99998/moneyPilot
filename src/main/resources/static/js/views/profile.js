import { api, ApiError, session } from '../api.js';
import { el, toast, pageHead, avatar } from '../ui.js';
import { HINT, passwordInput as passwordField } from '../password.js';
import { googleButton, googleErrorMessage } from '../google.js';

const CREATED_AT_FORMAT = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'long', timeZone: 'America/Sao_Paulo' });

function fieldRow(label, input, errorNode, hint, forId) {
    return el('div', { class: 'field' }, [
        el('label', { text: label, for: forId }),
        input,
        hint ? el('span', { class: 'meter-foot', text: hint }) : null,
        errorNode
    ]);
}

/** A API responde em inglês; a tela mostra a mensagem em português para cada campo. */
function fieldMessage(field, message) {
    if (field === 'currentPassword') return 'Senha atual incorreta.';
    if (field === 'newPassword') {
        // A regra de senha nova responde em português, com o motivo; só "diferente da atual" vem em inglês.
        return message.includes('different') ? 'A nova senha deve ser diferente da atual.' : message;
    }
    if (field === 'newEmail') {
        if (message.includes('different')) return 'Esse já é o e-mail atual da conta.';
        if (message.includes('in use')) return 'Este e-mail já está em uso por outra conta.';
        return 'Informe um e-mail válido.';
    }
    if (field === 'credential') return 'Não foi possível validar a conta Google. Tente de novo.';
    if (field === 'name') {
        return message.includes('blank') ? 'Informe o nome.' : 'O nome deve ter até 255 caracteres.';
    }
    return message;
}

/** Erro de campo vai embaixo do campo; a caixa geral só aparece quando não há campo (ex.: 429). */
function showErrors(error, fieldErrors, errorBox) {
    let shownOnField = false;
    if (error instanceof ApiError && error.fieldErrors) {
        Object.entries(error.fieldErrors).forEach(([field, message]) => {
            if (!fieldErrors[field]) return;
            fieldErrors[field].textContent = fieldMessage(field, message);
            shownOnField = true;
        });
    }
    if (shownOnField) return;
    errorBox.textContent = error?.message || 'Não foi possível salvar.';
    errorBox.hidden = false;
}

function resetErrors(fieldErrors, errorBox) {
    Object.values(fieldErrors).forEach((node) => { node.textContent = ''; });
    errorBox.hidden = true;
}

export async function renderProfile() {
    const view = el('div');
    await load(view);
    return view;
}

/**
 * Busca o perfil e desenha a tela dentro de `view`. Depois de uma ação que muda o que a tela mostra (vincular,
 * foto, criar senha), `reload` desenha de novo e leva o foco para `focusKey`, já que o botão clicado sumiu.
 */
async function load(view, focusKey) {
    const profile = await api.profile.get();
    session.setName(profile.name);
    session.setAvatar(profile.avatarUrl);

    const focus = {};
    const reload = async (key) => {
        try {
            await load(view, key);
            window.dispatchEvent(new CustomEvent('profile:updated'));
        } catch (error) {
            toast(error?.message || 'Não foi possível atualizar a tela.', 'error');
        }
    };

    view.replaceChildren(
        pageHead('Perfil', 'Seus dados e a segurança da conta'),
        dataCard(profile, reload, focus),
        emailCard(profile, focus),
        passwordCard(profile, focus),
        googleCard(profile, reload, focus)
    );
    focus[focusKey]?.focus();
}

function card(title, sub, body, heading) {
    return el('div', { class: 'card profile-card' }, [
        el('div', { class: 'card-head' }, [
            heading || el('h2', { text: title }),
            sub ? el('span', { class: 'sub', text: sub }) : null
        ]),
        body
    ]);
}

/** Título que recebe o foco quando a tela é redesenhada depois de uma ação. */
function focusableHeading(title, focus, key) {
    const heading = el('h2', { text: title, tabindex: '-1' });
    focus[key] = heading;
    return heading;
}

function photoBlock(profile, reload) {
    let hint = 'Sem foto, aparecem as suas iniciais. Vincule o Google para usar a sua foto.';
    if (profile.googleEmail) hint = 'Sem foto, aparecem as suas iniciais.';
    if (profile.avatarUrl) hint = 'A foto vem da sua conta Google.';
    if (profile.googleEmail && profile.avatarHidden) hint = 'Você removeu a foto: aparecem as suas iniciais.';

    const act = (button, action, done) => async () => {
        button.disabled = true;
        try {
            await action();
            toast(done, 'success');
            await reload('data');
        } catch (error) {
            toast(error?.message || 'Não foi possível salvar.', 'error');
            button.disabled = false;
        }
    };

    const buttons = [];
    if (profile.avatarUrl) {
        const remove = el('button', { class: 'btn-ghost', type: 'button', text: 'Remover foto' });
        remove.addEventListener('click', act(remove, () => api.profile.hideAvatar(), 'Foto removida.'));
        buttons.push(remove);
    }
    if (profile.googleEmail && profile.avatarHidden) {
        const restore = el('button', { class: 'btn-ghost', type: 'button', text: 'Usar foto do Google' });
        restore.addEventListener('click', act(restore, () => api.profile.useGoogleAvatar(), 'Foto do Google de volta.'));
        buttons.push(restore);
    }

    return el('div', { class: 'profile-photo' }, [
        avatar({ url: profile.avatarUrl, name: profile.name, size: 64 }),
        el('div', { class: 'profile-photo-body' }, [
            el('span', { class: 'meter-foot', text: hint }),
            buttons.length ? el('div', { class: 'profile-photo-actions' }, buttons) : null
        ])
    ]);
}

function dataCard(profile, reload, focus) {
    const nameInput = el('input', { type: 'text', autocomplete: 'name', required: true, maxlength: '255', value: profile.name });
    const nameErrors = { name: el('span', { class: 'error' }) };
    const nameBox = el('p', { class: 'dialog-error', hidden: true });
    const nameBtn = el('button', { class: 'btn-primary', type: 'submit', text: 'Salvar nome' });

    const nameForm = el('form', {
        onSubmit: async (event) => {
            event.preventDefault();
            nameBtn.disabled = true;
            resetErrors(nameErrors, nameBox);
            try {
                const updated = await api.profile.update(nameInput.value.trim());
                session.setName(updated.name);
                profile.name = updated.name;
                nameInput.value = updated.name;
                toast('Nome atualizado.', 'success');
                // O rodapé do menu mostra o nome; só ele precisa ser atualizado.
                window.dispatchEvent(new CustomEvent('profile:updated'));
            } catch (error) {
                showErrors(error, nameErrors, nameBox);
            }
            nameBtn.disabled = false;
        }
    }, [
        nameBox,
        fieldRow('Nome', nameInput, nameErrors.name),
        fieldRow('E-mail', el('input', { type: 'email', value: profile.email, disabled: true }), null,
            'Para trocar o e-mail, use o cartão abaixo.'),
        fieldRow('Conta criada em', el('input', {
            type: 'text', value: CREATED_AT_FORMAT.format(new Date(profile.createdAt)), disabled: true
        })),
        nameBtn
    ]);

    return card('Dados', null, el('div', {}, [photoBlock(profile, reload), nameForm]), focusableHeading('Dados', focus, 'data'));
}

function passwordCard(profile, focus) {
    if (!profile.hasPassword) return createPasswordCard(profile);

    const currentField = passwordField({ id: 'profile-current-password', autocomplete: 'current-password' });
    const currentInput = currentField.input;
    const newField = passwordField({
        id: 'profile-new-password',
        autocomplete: 'new-password',
        describedBy: 'profile-new-password-hint profile-new-password-error',
        meter: true,
        getContext: () => ({ email: profile.email, name: profile.name }),
        minlength: '10',
        maxlength: '64'
    });
    const newInput = newField.input;
    const passwordErrors = { currentPassword: el('span', { class: 'error' }), newPassword: el('span', { class: 'error', id: 'profile-new-password-error' }) };
    const passwordBox = el('p', { class: 'dialog-error', hidden: true });
    const passwordBtn = el('button', { class: 'btn-primary', type: 'submit', text: 'Trocar senha' });

    const passwordForm = el('form', {
        onSubmit: async (event) => {
            event.preventDefault();
            passwordBtn.disabled = true;
            resetErrors(passwordErrors, passwordBox);
            try {
                await api.changePassword(currentInput.value, newInput.value);
                currentInput.value = '';
                newInput.value = '';
                newField.refresh();
                toast('Senha alterada. Os outros aparelhos foram desconectados.', 'success');
            } catch (error) {
                showErrors(error, passwordErrors, passwordBox);
            }
            passwordBtn.disabled = false;
        }
    }, [
        passwordBox,
        fieldRow('Senha atual', currentField.control, passwordErrors.currentPassword, null, 'profile-current-password'),
        el('div', { class: 'field' }, [
            el('label', { for: 'profile-new-password', text: 'Nova senha' }),
            newField.control,
            el('span', { class: 'meter-foot', id: 'profile-new-password-hint', text: `${HINT} Diferente da atual.` }),
            newField.meter,
            passwordErrors.newPassword
        ]),
        passwordBtn
    ]);

    return card('Senha', 'Ao trocar, os outros aparelhos saem da conta.', passwordForm);
}

/** Conta criada pelo Google: a senha nasce pelo mesmo link do "esqueci a senha". */
function createPasswordCard(profile) {
    const status = el('p', { class: 'meter-foot', role: 'status', tabindex: '-1' });
    const errorBox = el('p', { class: 'dialog-error', hidden: true, role: 'alert' });
    const button = el('button', { class: 'btn-primary', type: 'button', text: 'Criar senha' });

    button.addEventListener('click', async () => {
        button.disabled = true;
        errorBox.hidden = true;
        try {
            await api.forgotPassword(profile.email);
            status.textContent = `Enviamos um link para ${profile.email}. Ele vale por 30 minutos; confira também a caixa de spam.`;
            status.focus();
        } catch (error) {
            errorBox.textContent = error?.message || 'Não foi possível enviar agora.';
            errorBox.hidden = false;
        }
        button.disabled = false;
    });

    return card('Senha', null, el('div', {}, [
        el('p', { text: 'Sua conta ainda não tem senha: você entra pelo Google. Crie uma para também entrar com e-mail e senha, trocar o e-mail e poder desvincular o Google.' }),
        errorBox,
        status,
        button
    ]));
}

function emailCard(profile) {
    if (!profile.hasPassword) {
        return card('E-mail', null, el('p', {
            text: 'Para trocar o e-mail da conta, crie uma senha antes, no cartão Senha.'
        }));
    }

    const emailInput = el('input', { type: 'email', autocomplete: 'email', required: true, maxlength: '255', value: profile.pendingEmail || '' });
    const emailPasswordField = passwordField({ id: 'profile-email-password', autocomplete: 'current-password' });
    const emailPasswordInput = emailPasswordField.input;
    const emailErrors = { newEmail: el('span', { class: 'error' }), currentPassword: el('span', { class: 'error' }) };
    const emailBox = el('p', { class: 'dialog-error', hidden: true });
    const pendingNote = el('p', { class: 'meter-foot' });
    const emailBtn = el('button', { class: 'btn-primary', type: 'submit' });

    function showPending(pendingEmail) {
        pendingNote.textContent = pendingEmail
            ? `Aguardando confirmação em ${pendingEmail}. Até lá, o e-mail atual continua valendo.`
            : '';
        pendingNote.hidden = !pendingEmail;
        emailBtn.textContent = pendingEmail ? 'Reenviar link' : 'Enviar link de confirmação';
    }
    showPending(profile.pendingEmail);

    const emailForm = el('form', {
        onSubmit: async (event) => {
            event.preventDefault();
            emailBtn.disabled = true;
            resetErrors(emailErrors, emailBox);
            const newEmail = emailInput.value.trim().toLowerCase();
            try {
                await api.requestEmailChange(newEmail, emailPasswordInput.value);
                emailInput.value = newEmail;
                emailPasswordInput.value = '';
                showPending(newEmail);
                toast(`Enviamos um link de confirmação para ${newEmail}.`, 'success');
            } catch (error) {
                showErrors(error, emailErrors, emailBox);
            }
            emailBtn.disabled = false;
        }
    }, [
        emailBox,
        pendingNote,
        fieldRow('Novo e-mail', emailInput, emailErrors.newEmail,
            'Enviaremos um link para este endereço. A troca só vale depois que você abri-lo.'),
        fieldRow('Senha atual', emailPasswordField.control, emailErrors.currentPassword, null, 'profile-email-password'),
        emailBtn
    ]);

    return card('E-mail', 'O link vale por 24 horas e só pode ser usado uma vez.', emailForm);
}

function googleCard(profile, reload, focus) {
    const errorBox = el('p', { class: 'dialog-error', hidden: true, role: 'alert' });
    const fieldErrors = {
        currentPassword: el('span', { class: 'error', id: 'profile-google-password-error' }),
        credential: el('span', { class: 'error' })
    };
    const heading = focusableHeading('Google', focus, 'google');

    function fail(error) {
        resetErrors(fieldErrors, errorBox);
        if (error instanceof ApiError && error.fieldErrors) showErrors(error, fieldErrors, errorBox);
        else {
            errorBox.textContent = googleErrorMessage(error);
            errorBox.hidden = false;
        }
    }

    if (profile.googleEmail) {
        return card('Google', null, linkedGoogle(profile, errorBox, fieldErrors, fail, reload), heading);
    }

    if (!profile.hasPassword) {
        return card('Google', null, el('p', {
            text: 'Para vincular uma conta Google, crie uma senha antes, no cartão Senha.'
        }), heading);
    }

    const passwordControl = passwordField({
        id: 'profile-google-password', autocomplete: 'current-password', describedBy: 'profile-google-password-error'
    });
    const google = googleButton({
        text: 'continue_with',
        onCredential: async (credential) => {
            resetErrors(fieldErrors, errorBox);
            if (!passwordControl.input.value) {
                fieldErrors.currentPassword.textContent = 'Informe a senha atual para vincular.';
                passwordControl.input.focus();
                return;
            }
            try {
                await api.profile.linkGoogle(credential, passwordControl.input.value);
                toast('Google vinculado.', 'success');
                await reload('google');
            } catch (error) {
                fail(error);
            }
        }
    });

    return card('Google', 'Entre com um clique.', el('div', {}, [
        errorBox,
        el('p', { class: 'meter-foot', text: 'Informe a senha atual para confirmar que é você e escolha a conta Google. Ela pode ter um e-mail diferente do da conta.' }),
        fieldRow('Senha atual', passwordControl.control, fieldErrors.currentPassword, null, 'profile-google-password'),
        google,
        fieldErrors.credential
    ]), heading);
}

function linkedGoogle(profile, errorBox, fieldErrors, fail, reload) {
    const connected = el('p', {}, ['Conectado como ', el('strong', { text: profile.googleEmail })]);

    if (!profile.hasPassword) {
        const note = el('p', {
            class: 'meter-foot',
            id: 'profile-google-unlink-note',
            text: 'Para desvincular, crie uma senha antes: é ela que garante o seu acesso sem o Google.'
        });
        return el('div', {}, [
            connected,
            note,
            el('button', {
                class: 'btn-ghost btn-danger', type: 'button', text: 'Desvincular', disabled: true,
                'aria-describedby': 'profile-google-unlink-note'
            })
        ]);
    }

    const passwordControl = passwordField({
        id: 'profile-google-password', autocomplete: 'current-password', describedBy: 'profile-google-password-error'
    });
    const button = el('button', { class: 'btn-ghost btn-danger', type: 'submit', text: 'Desvincular' });

    return el('form', {
        onSubmit: async (event) => {
            event.preventDefault();
            button.disabled = true;
            resetErrors(fieldErrors, errorBox);
            try {
                await api.profile.unlinkGoogle(passwordControl.input.value);
                toast('Google desvinculado.', 'success');
                await reload('google');
                return;
            } catch (error) {
                fail(error);
            }
            button.disabled = false;
        }
    }, [
        errorBox,
        connected,
        fieldRow('Senha atual', passwordControl.control, fieldErrors.currentPassword, 'Pedimos a senha para confirmar que é você.', 'profile-google-password'),
        button
    ]);
}
