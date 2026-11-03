import { api, session } from '../api.js';
import { el } from '../ui.js';
import { passwordInput as passwordField } from '../password.js';
import { googleButton } from '../google.js';

export const DELETE_WORD = 'EXCLUIR';

/**
 * Cartão "Excluir conta" do perfil (spec 012). Conta com senha confirma com a senha; conta só com Google, entrando
 * de novo com o Google. Nos dois casos a pessoa digita EXCLUIR, palavra que só existe aqui na tela.
 *
 * `helpers` vem do perfil: `fieldRow`, `showErrors` e `resetErrors`, para o cartão se comportar como os outros.
 */
export function deleteCard(profile, { fieldRow, showErrors, resetErrors }) {
    const wordInput = el('input', {
        id: 'profile-delete-word',
        name: 'confirmation',
        type: 'text',
        autocomplete: 'off',
        autocapitalize: 'characters',
        spellcheck: 'false',
        placeholder: `${DELETE_WORD}…`,
        'aria-describedby': 'profile-delete-word-hint profile-delete-word-error'
    });
    const errors = {
        currentPassword: el('span', { class: 'error', id: 'profile-delete-password-error' }),
        credential: el('span', { class: 'error', id: 'profile-delete-google-error' })
    };
    const wordError = el('span', { class: 'error', id: 'profile-delete-word-error' });
    const errorBox = el('p', { class: 'dialog-error', hidden: true, role: 'alert' });
    const confirmed = () => wordInput.value.trim().toUpperCase() === DELETE_WORD;

    async function remove(confirmation, button, firstField) {
        resetErrors(errors, errorBox);
        if (button) {
            button.disabled = true;
            button.textContent = 'Excluindo…';
        }
        try {
            await api.profile.deleteAccount(confirmation);
            session.clear();
            window.dispatchEvent(new CustomEvent('account:deleted'));
            return;
        } catch (error) {
            showErrors(error, errors, errorBox);
            // Erro de campo: o foco vai para o campo, para quem usa leitor de tela ou teclado.
            if (Object.values(errors).some((node) => node.textContent)) firstField?.focus();
        }
        if (button) {
            button.textContent = 'Excluir minha conta';
            button.disabled = !confirmed();
        }
    }

    const farewell = el('div', { class: 'farewell', role: 'group', 'aria-labelledby': 'profile-delete-farewell' }, [
        el('p', { class: 'farewell-title', id: 'profile-delete-farewell' }, [
            'Sentimos muito por ver você ir ',
            el('span', { 'aria-hidden': 'true', text: '😢' })
        ]),
        el('p', {
            text: 'Mas ficamos felizes por você ter usado o moneyPilot. As portas continuam abertas: quando quiser voltar, é só criar um novo cadastro.'
        })
    ]);

    const consequences = el('ul', { class: 'delete-list' }, [
        el('li', { text: 'A conta e todos os dados (contas, lançamentos, categorias, orçamentos e títulos) são excluídos de imediato.' }),
        el('li', { text: 'Os seus chamados de suporte são encerrados e apagados.' }),
        el('li', { text: 'Todos os aparelhos saem da conta.' }),
        el('li', { class: 'delete-strong', text: 'Não há como desfazer.' })
    ]);

    const wordField = el('div', { class: 'field' }, [
        el('label', { for: 'profile-delete-word', text: `Para confirmar, digite ${DELETE_WORD}` }),
        wordInput,
        el('span', {
            class: 'meter-foot',
            id: 'profile-delete-word-hint',
            text: profile.hasPassword
                ? 'O botão só é liberado depois que você digitar a palavra.'
                : 'O botão do Google só é liberado depois que você digitar a palavra.'
        }),
        wordError
    ]);

    let confirmControl;
    if (profile.hasPassword) {
        const passwordControl = passwordField({
            id: 'profile-delete-password', autocomplete: 'current-password', describedBy: 'profile-delete-password-error'
        });
        const button = el('button', {
            class: 'btn-ghost btn-danger btn-delete', type: 'submit', text: 'Excluir minha conta', disabled: true
        });
        wordInput.addEventListener('input', () => { button.disabled = !confirmed(); });
        confirmControl = el('form', {
            onSubmit: async (event) => {
                event.preventDefault();
                if (!confirmed()) return;
                await remove({ currentPassword: passwordControl.input.value }, button, passwordControl.input);
            }
        }, [
            errorBox,
            fieldRow('Senha atual', passwordControl.control, errors.currentPassword,
                'Pedimos a senha para confirmar que é você.', 'profile-delete-password'),
            wordField,
            button
        ]);
    } else {
        // Sem senha, o botão do Google é a confirmação. Ele fica inerte até a palavra ser digitada.
        const googleHint = el('p', {
            class: 'meter-foot',
            hidden: true,
            text: 'Depois de digitar a palavra, entre de novo com o Google para confirmar que é você. Use a conta Google vinculada.'
        });
        const google = googleButton({
            text: 'continue_with',
            onReady: () => { googleHint.hidden = false; },
            onCredential: async (credential) => {
                resetErrors(errors, errorBox);
                if (!confirmed()) {
                    wordError.textContent = `Digite ${DELETE_WORD} para confirmar.`;
                    wordInput.focus();
                    return;
                }
                await remove({ credential });
            }
        });
        const sync = () => {
            if (confirmed()) {
                google.removeAttribute('inert');
                google.classList.remove('is-locked');
                wordError.textContent = '';
            } else {
                google.setAttribute('inert', '');
                google.classList.add('is-locked');
            }
        };
        sync();
        wordInput.addEventListener('input', sync);
        confirmControl = el('div', {}, [errorBox, wordField, googleHint, google, errors.credential]);
    }

    return el('section', { class: 'card profile-card danger-card', 'aria-labelledby': 'profile-delete-title' }, [
        el('div', { class: 'card-head' }, [
            el('h2', { id: 'profile-delete-title', text: 'Excluir conta' }),
            el('span', { class: 'sub', text: 'Ação permanente' })
        ]),
        farewell,
        consequences,
        confirmControl
    ]);
}
