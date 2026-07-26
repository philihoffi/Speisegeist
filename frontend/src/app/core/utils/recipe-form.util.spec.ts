import {
  emptyIngredientDraft,
  emptyStepDraft,
  toIngredientDrafts,
  toStepDrafts,
  buildIngredientsPayload,
  buildStepsPayload,
} from './recipe-form.util';
import { IngredientDraft, StepDraft } from '../models/recipe.model';

describe('recipe-form.util', () => {
  describe('emptyIngredientDraft', () => {
    it('returns a blank row', () => {
      expect(emptyIngredientDraft()).toEqual({ name: '', quantity: null, unit: '', notes: '' });
    });

    it('returns a fresh object each call', () => {
      expect(emptyIngredientDraft()).not.toBe(emptyIngredientDraft());
    });
  });

  describe('emptyStepDraft', () => {
    it('returns a blank row', () => {
      expect(emptyStepDraft()).toEqual({ instruction: '', durationMinutes: null });
    });
  });

  describe('toIngredientDrafts', () => {
    it('maps all fields', () => {
      const result = toIngredientDrafts([
        { name: 'Tofu', quantity: 200, unit: 'g', notes: 'gepresst' },
      ]);

      expect(result).toEqual([{ name: 'Tofu', quantity: 200, unit: 'g', notes: 'gepresst' }]);
    });

    it('normalises undefined optionals to null / empty string', () => {
      const result = toIngredientDrafts([{ name: 'Salz' }]);

      expect(result[0]).toEqual({ name: 'Salz', quantity: null, unit: '', notes: '' });
    });

    it('keeps quantity 0 instead of coercing it to null', () => {
      const result = toIngredientDrafts([{ name: 'Zucker', quantity: 0 }]);

      expect(result[0].quantity).toBe(0);
    });

    it('returns an empty array for no ingredients', () => {
      expect(toIngredientDrafts([])).toEqual([]);
    });
  });

  describe('toStepDrafts', () => {
    it('maps instruction and duration', () => {
      const result = toStepDrafts([
        { stepNumber: 1, instruction: 'Anbraten', durationMinutes: 5 },
      ]);

      expect(result).toEqual([{ instruction: 'Anbraten', durationMinutes: 5 }]);
    });

    it('normalises missing duration to null', () => {
      const result = toStepDrafts([{ stepNumber: 1, instruction: 'Servieren' }]);

      expect(result[0].durationMinutes).toBeNull();
    });
  });

  describe('buildIngredientsPayload', () => {
    const row = (over: Partial<IngredientDraft> = {}): IngredientDraft => ({
      name: 'Tofu', quantity: 200, unit: 'g', notes: '', ...over,
    });

    it('drops rows with a blank name', () => {
      const result = buildIngredientsPayload([row(), row({ name: '   ' }), row({ name: '' })]);

      expect(result).toHaveLength(1);
    });

    it('trims the name', () => {
      const result = buildIngredientsPayload([row({ name: '  Tofu  ' })]);

      expect(result[0].name).toBe('Tofu');
    });

    it('converts null quantity to undefined so the field is omitted', () => {
      const result = buildIngredientsPayload([row({ quantity: null })]);

      expect(result[0].quantity).toBeUndefined();
    });

    it('keeps quantity 0', () => {
      const result = buildIngredientsPayload([row({ quantity: 0 })]);

      expect(result[0].quantity).toBe(0);
    });

    it('converts blank unit and notes to undefined', () => {
      const result = buildIngredientsPayload([row({ unit: '   ', notes: '' })]);

      expect(result[0].unit).toBeUndefined();
      expect(result[0].notes).toBeUndefined();
    });

    it('trims unit and notes when present', () => {
      const result = buildIngredientsPayload([row({ unit: ' g ', notes: ' fein gehackt ' })]);

      expect(result[0].unit).toBe('g');
      expect(result[0].notes).toBe('fein gehackt');
    });

    it('returns an empty array when every row is blank', () => {
      expect(buildIngredientsPayload([row({ name: '' }), row({ name: '  ' })])).toEqual([]);
    });
  });

  describe('buildStepsPayload', () => {
    const step = (over: Partial<StepDraft> = {}): StepDraft => ({
      instruction: 'Anbraten', durationMinutes: 5, ...over,
    });

    it('drops rows with a blank instruction', () => {
      const result = buildStepsPayload([step(), step({ instruction: '  ' })]);

      expect(result).toHaveLength(1);
    });

    it('renumbers steps from 1 after dropping blanks', () => {
      const result = buildStepsPayload([
        step({ instruction: 'Erster' }),
        step({ instruction: '   ' }),
        step({ instruction: 'Zweiter' }),
        step({ instruction: 'Dritter' }),
      ]);

      expect(result.map(s => s.stepNumber)).toEqual([1, 2, 3]);
      expect(result.map(s => s.instruction)).toEqual(['Erster', 'Zweiter', 'Dritter']);
    });

    it('trims the instruction', () => {
      const result = buildStepsPayload([step({ instruction: '  Anbraten  ' })]);

      expect(result[0].instruction).toBe('Anbraten');
    });

    it('converts null duration to undefined', () => {
      const result = buildStepsPayload([step({ durationMinutes: null })]);

      expect(result[0].durationMinutes).toBeUndefined();
    });

    it('keeps duration 0', () => {
      const result = buildStepsPayload([step({ durationMinutes: 0 })]);

      expect(result[0].durationMinutes).toBe(0);
    });

    it('returns an empty array when every row is blank', () => {
      expect(buildStepsPayload([step({ instruction: '' })])).toEqual([]);
    });
  });
});
