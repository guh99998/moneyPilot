import { api, ApiError, session } from '../api.js';
import { el, toast, pageHead } from '../ui.js';

const CREATED_AT_FORMAT = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'long', timeZone: 'America/Sao_Paulo' });

function fieldRow(label, input, errorNode, hint) {
    return el('div', { class: 'field' }, [
        el('label', { text: label }),
        input,
        hint ? el('span', { class: 'meter-foot', text: hint }) : null,
        errorNode
    ]);
}

/** A API responde em inglês; a tela mostra a mensagem em português para cada campo. */
function fieldMessage(field, message) {
    if (field === 'currentPassword') return 'Senha atual incorreta.';
    if (field === 'newPassword') {
        return message.includes('different')
            ? 'A nova senha deve ser diferente da atual.'
            : 'A nova senha deve ter no mínimo 8 caracteres.';
    }
    if (field === 'newEmail') {
        if (message.includes('different')) return 'Esse já é o e-mail atual da conta.';
        if (message.includes('in use')) return 'Este e-mail já está em uso por outra conta.';
        return 'Informe um e-mail válido.';
    }
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
    const profile = await api.profile.get();
    session.setName(profile.name);

    const view = el('div');
    view.append(pageHead('Perfil', 'Seus dados e a segurança da conta'));

    // Dados
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

    // Senha
    const currentInput = el('input', { type: 'password', autocomplete: 'current-password', required: true });
    const newInput = el('input', { type: 'password', autocomplete: 'new-password', required: true, minlength: '8', maxlength: '64' });
    const passwordErrors = { currentPassword: el('span', { class: 'error' }), newPassword: el('span', { class: 'error' }) };
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
                toast('Senha alterada. Os outros aparelhos foram desconectados.', 'success');
            } catch (error) {
                showErrors(error, passwordErrors, passwordBox);
            }
            passwordBtn.disabled = false;
        }
    }, [
        passwordBox,
        fieldRow('Senha atual', currentInput, passwordErrors.currentPassword),
        fieldRow('Nova senha', newInput, passwordErrors.newPassword, 'De 8 a 64 caracteres, diferente da atual.'),
        passwordBtn
    ]);

    // E-mail
    const emailInput = el('input', { type: 'email', autocomplete: 'email', required: true, maxlength: '255', value: profile.pendingEmail || '' });
    const emailPasswordInput = el('input', { type: 'password', autocomplete: 'current-password', required: true });
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
        fieldRow('Senha atual', emailPasswordInput, emailErrors.currentPassword),
        emailBtn
    ]);

    view.append(
        el('div', { class: 'card profile-card' }, [
            el('div', { class: 'card-head' }, [el('h2', { text: 'Dados' })]),
            nameForm
        ]),
        el('div', { class: 'card profile-card' }, [
            el('div', { class: 'card-head' }, [
                el('h2', { text: 'E-mail' }),
                el('span', { class: 'sub', text: 'O link vale por 24 horas e só pode ser usado uma vez.' })
            ]),
            emailForm
        ]),
        el('div', { class: 'card profile-card' }, [
            el('div', { class: 'card-head' }, [
                el('h2', { text: 'Senha' }),
                el('span', { class: 'sub', text: 'Ao trocar, os outros aparelhos saem da conta.' })
            ]),
            passwordForm
        ])
    );

    return view;
}
