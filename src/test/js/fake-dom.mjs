// Minimal DOM for testing the web views under node:test: just what ui.js `el()` and the views touch.
// It is not a browser; add to it only when a view starts needing more.

const BOOLEAN_ATTRIBUTES = ['hidden', 'disabled', 'required'];

class FakeText {
    constructor(data) {
        this.data = String(data);
        this.parent = null;
    }

    get textContent() {
        return this.data;
    }
}

class FakeElement {
    constructor(tagName) {
        this.tagName = tagName.toUpperCase();
        this.attributes = new Map();
        this.children = [];
        this.parent = null;
        this.listeners = new Map();
        this.dataset = {};
        this.className = '';
        this.value = '';
        const element = this;
        this.classList = {
            add: (name) => element.className = [...new Set([...element.className.split(' ').filter(Boolean), name])].join(' '),
            contains: (name) => element.className.split(' ').includes(name)
        };
    }

    setAttribute(name, value) {
        this.attributes.set(name, String(value));
    }

    getAttribute(name) {
        return this.attributes.has(name) ? this.attributes.get(name) : null;
    }

    hasAttribute(name) {
        return this.attributes.has(name);
    }

    removeAttribute(name) {
        this.attributes.delete(name);
    }

    get textContent() {
        return this.children.map((child) => child.textContent).join('');
    }

    set textContent(text) {
        this.replaceChildren(text);
    }

    append(...nodes) {
        nodes.forEach((node) => {
            const child = typeof node === 'string' ? new FakeText(node) : node;
            child.remove?.();
            child.parent = this;
            this.children.push(child);
        });
    }

    replaceChildren(...nodes) {
        this.children.forEach((child) => { child.parent = null; });
        this.children = [];
        this.append(...nodes);
    }

    after(node) {
        node.remove();
        const siblings = this.parent.children;
        node.parent = this.parent;
        siblings.splice(siblings.indexOf(this) + 1, 0, node);
    }

    remove() {
        if (!this.parent) return;
        this.parent.children = this.parent.children.filter((child) => child !== this);
        this.parent = null;
    }

    addEventListener(type, listener) {
        this.listeners.set(type, [...(this.listeners.get(type) || []), listener]);
    }

    /** Fires the listeners and waits for async ones, like the browser would let the page run. */
    async dispatch(type) {
        const event = { type, target: this, preventDefault() {} };
        await Promise.all((this.listeners.get(type) || []).map((listener) => listener(event)));
    }

    focus() {
        globalThis.document.activeElement = this;
    }
}

BOOLEAN_ATTRIBUTES.forEach((name) => {
    Object.defineProperty(FakeElement.prototype, name, {
        get() { return this.hasAttribute(name); },
        set(on) { if (on) this.setAttribute(name, ''); else this.removeAttribute(name); }
    });
});

/** Every element under `root` (itself included) that satisfies `predicate`, in document order. */
export function findAll(root, predicate) {
    const found = predicate(root) ? [root] : [];
    (root.children || []).forEach((child) => {
        if (child instanceof FakeElement) found.push(...findAll(child, predicate));
    });
    return found;
}

export function findByTag(root, tag) {
    return findAll(root, (node) => node.tagName === tag.toUpperCase());
}

export function findByText(root, tag, text) {
    return findAll(root, (node) => node.tagName === tag.toUpperCase() && node.textContent === text)[0];
}

/** Installs the browser globals the views need. Call once per test file, before importing a view. */
export function installFakeDom() {
    const storage = new Map();
    const toasts = new FakeElement('div');

    globalThis.localStorage = {
        getItem: (key) => (storage.has(key) ? storage.get(key) : null),
        setItem: (key, value) => storage.set(key, String(value)),
        removeItem: (key) => storage.delete(key)
    };
    globalThis.document = {
        activeElement: null,
        body: new FakeElement('body'),
        createElement: (tag) => new FakeElement(tag),
        getElementById: (id) => (id === 'toasts' ? toasts : null)
    };
    globalThis.window = {
        location: { origin: 'http://localhost', hash: '' },
        history: {
            replaceState: (state, title, url) => { globalThis.window.location.hash = url; }
        },
        dispatchEvent: () => {}
    };

    return { toasts };
}
