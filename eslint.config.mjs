import js from '@eslint/js';
import globals from 'globals';
import tseslint from 'typescript-eslint';

export default tseslint.config(
    {
        ignores: [
            '.build/**',
            'build/**',
            'dist/**',
            'coverage/**',
        ],
    },
    {
        files: ['src/**/*.ts', 'test/**/*.ts', 'example/**/*.ts'],
        extends: [
            js.configs.recommended,
            ...tseslint.configs.recommended,
        ],
        languageOptions: {
            globals: {
                ...globals.browser,
                ...globals.node,
                ...globals.jest,
            },
        },
        rules: {
            '@typescript-eslint/no-explicit-any': 'off',
            '@typescript-eslint/no-unused-vars': ['error', { caughtErrorsIgnorePattern: '^ignore$' }],
            'preserve-caught-error': 'off',
        },
    },
);
