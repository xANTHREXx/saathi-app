import { assertEquals } from "jsr:@std/assert@1";
import { scrub } from "./scrub.ts";

Deno.test("scrubHidesIndianPhoneNumbers", () => {
  assertEquals(scrub("call +91 98765 43210 now"), "call [number] now");
  assertEquals(scrub("call 9876543210 now"), "call [number] now");
  assertEquals(scrub("call 98765-43210 now"), "call [number] now");
  assertEquals(scrub("call +919876543210"), "call [number]");
});

Deno.test("scrubHidesEmails", () => {
  assertEquals(scrub("failed for riya.k+test@mail.example.co.in!"), "failed for [email]!");
});

Deno.test("scrubHidesCoordinatePairs", () => {
  assertEquals(scrub("at 12.97167,77.59456 stopped"), "at [place] stopped");
  assertEquals(scrub("at 12.9716, 77.5946 stopped"), "at [place] stopped");
  assertEquals(scrub("at -33.86882,151.20929"), "at [place]");
});

Deno.test("scrubLeavesStackFramesAndShortNumbersAlone", () => {
  const frame = "\tat app.saathi.ui.MainActivity.onCreate(MainActivity.kt:123)";
  assertEquals(scrub(frame), frame);
  assertEquals(scrub("Foo.kt:123"), "Foo.kt:123");
  assertEquals(scrub("Foo.kt:12345"), "Foo.kt:12345");
  assertEquals(scrub("version 1.5, 2 items, 12.5,3.25"), "version 1.5, 2 items, 12.5,3.25");
});

Deno.test("scrubIsNotQuadraticOnLongInput", () => {
  const t0 = Date.now();
  scrub("a".repeat(8000));
  scrub("1 ".repeat(4000));
  assertEquals(Date.now() - t0 < 2000, true);
});
