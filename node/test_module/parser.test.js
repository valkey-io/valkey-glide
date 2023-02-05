import { Parser } from "../build-ts";

describe("Parserg", () => {
    const encoder = new TextEncoder();

    const parseAndTest = (array, expected) => {
        const parser = new Parser();

        expect(parser.parse(array)).toEqual(expected);
    };

    it("should parse nil", () => {
        const array = encoder.encode("$-1\r\n");

        parseAndTest(array, null);
    });

    it("should parse string", () => {
        const array = encoder.encode("+OKdk\r\n");

        parseAndTest(array, "OKdk");
    });

    it("should parse number", () => {
        const array = encoder.encode(":1000\r\n");

        parseAndTest(array, 1000);
    });

    it("should parse array", () => {
        const array = encoder.encode(
            "*5\r\n:11\r\n:222\r\n:3333\r\n:44444\r\n$7\r\nhello\r\n\r\n"
        );

        parseAndTest(array, [11, 222, 3333, 44444, "hello\r\n"]);
    });

    it("should parse bulk", () => {
        const array = encoder.encode(
            "$48\r\nhello\r\nhello\r\nhello\r\n汉字\r\nhello\r\nhello\r\nhello\r\n"
        );

        parseAndTest(
            array,
            "hello\r\nhello\r\nhello\r\n汉字\r\nhello\r\nhello\r\nhello"
        );
    });

    it("should return error on partial value", () => {
        const array = encoder.encode("+OKdk");

        const parser = new Parser();

        expect(() => parser.parse(array)).toThrow();
    });
});
