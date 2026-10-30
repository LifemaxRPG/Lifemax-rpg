// Bug-catching rules only. Style is not enforced on the 80-segment runtime: it is one global script
// whose layering relies on re-assigning functions, so formatting or no-undef rules would be noise.
export default [
  {
    files: ['**/*.js'],
    languageOptions: { ecmaVersion: 2023, sourceType: 'script' },
    rules: {
      'no-dupe-keys': 'error', 'no-dupe-args': 'error', 'no-dupe-else-if': 'error', 'no-duplicate-case': 'error',
      'no-unreachable': 'error', 'no-unsafe-finally': 'error', 'no-self-assign': 'error', 'no-cond-assign': 'error',
      'no-func-assign': 'error', 'no-const-assign': 'error', 'no-ex-assign': 'error', 'valid-typeof': 'error',
      'use-isnan': 'error', 'no-sparse-arrays': 'error', 'no-unsafe-negation': 'error', 'no-empty-pattern': 'error',
      'no-dupe-class-members': 'error', 'no-loss-of-precision': 'error', 'no-unused-labels': 'error',
      'no-redeclare': 'error', 'no-shadow-restricted-names': 'error', 'no-delete-var': 'error',
      'getter-return': 'error', 'no-compare-neg-zero': 'error',
    },
  },
];
