// Робота з номером картки без сторонніх бібліотек.
export const CardUtil = {
  // Luhn — та сама перевірка, яку робить будь-який платіжний шлюз.
  isValidNumber(cardNumber: string): boolean {
    const digits = cardNumber.replace(/\D/g, '');

    if (digits.length < 13 || digits.length > 19) {
      return false;
    }

    let sum = 0;
    let double = false;

    for (let i = digits.length - 1; i >= 0; i -= 1) {
      let value = Number(digits[i]);

      if (double) {
        value *= 2;

        if (value > 9) {
          value -= 9;
        }
      }

      sum += value;
      double = !double;
    }

    return sum % 10 === 0;
  },

  detectBrand(cardNumber: string): string {
    const digits = cardNumber.replace(/\D/g, '');

    if (digits.startsWith('4')) {
      return 'VISA';
    }

    if (/^5[1-5]/.test(digits)) {
      return 'MASTERCARD';
    }

    if (/^3[47]/.test(digits)) {
      return 'AMEX';
    }

    return 'CARD';
  },

  // Зберігаємо тільки останні 4 цифри — повний номер на сервері не лишається.
  last4(cardNumber: string): string {
    return cardNumber.replace(/\D/g, '').slice(-4);
  },

  isExpired(expMonth: number, expYear: number, now = new Date()): boolean {
    const firstDayOfNextMonth = new Date(expYear, expMonth, 1);

    return firstDayOfNextMonth <= now;
  },
};
