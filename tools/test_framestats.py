"""Tests de tools/framestats.py (009 P5). Se ejecuta con `python -m unittest tools/test_framestats.py`."""
import os
import subprocess
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import framestats  # noqa: E402

HEADER = ("Flags,FrameTimelineVsyncId,IntendedVsync,Vsync,InputEventId,HandleInputStart,AnimationStart,"
          "PerformTraversalsStart,DrawStart,FrameDeadline,FrameStartTime,FrameInterval,WorkloadTarget,SyncQueued,"
          "SyncStart,IssueDrawCommandsStart,SwapBuffers,FrameCompleted,DequeueBufferDuration,QueueBufferDuration,"
          "GpuCompleted,SwapBuffersCompleted,DisplayPresentTime,CommandSubmissionCompleted,")

MS = 1_000_000


def row(start_ms, dur_ms, input_id=0, flags=0, app_ms=None, deadline_ms=33):
    """
    Una fila de framestats con lo que lee framestats.py; el resto a 0. La app tarda la mitad si no se dice y el plazo
    de Android es de 33 ms si no se dice.
    """
    cells = {name: "0" for name in HEADER.rstrip(",").split(",")}
    intended = 128_000_000_000_000 + int(start_ms * MS)
    cells.update({
        "Flags": str(flags),
        "IntendedVsync": str(intended),
        "Vsync": str(intended),
        "InputEventId": str(input_id),
        "FrameDeadline": str(intended + int(deadline_ms * MS)),
        "FrameCompleted": str(intended + int(dur_ms * MS)),
        "SyncQueued": str(intended + int((dur_ms / 2 if app_ms is None else app_ms) * MS)),
    })
    return ",".join(cells.values()) + ","


def dump(*rows):
    """Un volcado de `dumpsys gfxinfo … framestats` reducido: texto antes, la cabecera y las filas."""
    return "\n".join(["Applications Graphics Acceleration Info:", "---PROFILEDATA---", HEADER, *rows,
                      "---PROFILEDATA---", ""])


def gesture(*dumps):
    return framestats.GESTURE_MARK + "\n" + "".join(dumps)


class FirstFrameTest(unittest.TestCase):
    def test_first_frame_is_the_first_with_input(self):
        text = gesture(dump(row(0, 15), row(16, 16), row(33, 38, input_id=-969601087, app_ms=12),
                            row(50, 25, input_id=7, app_ms=3)))
        self.assertEqual(framestats.first_frames(text), [12.0])
        self.assertEqual(framestats.first_frames(text, "total"), [38.0])

    def test_repeated_dumps_count_each_frame_once(self):
        first = dump(row(0, 15), row(33, 20, input_id=5, app_ms=4))
        later = dump(row(0, 15), row(33, 20, input_id=5, app_ms=4), row(50, 16, input_id=6, app_ms=2))
        self.assertEqual(framestats.first_frames(gesture(first, later)), [4.0])

    def test_one_figure_per_gesture_in_order(self):
        text = (gesture(dump(row(0, 15), row(16, 40, input_id=1, app_ms=20)))
                + gesture(dump(row(0, 14, input_id=2, app_ms=2)))
                + gesture(dump(row(0, 30, input_id=3, app_ms=16.9))))
        self.assertEqual(framestats.first_frames(text), [20.0, 2.0, 16.9])
        self.assertEqual(framestats.first_frames(text, "total"), [40.0, 14.0, 30.0])

    def test_gesture_without_input_is_missing(self):
        text = gesture(dump(row(0, 15))) + gesture(dump(row(0, 12, input_id=4, app_ms=3)))
        self.assertEqual(framestats.first_frames(text), [None, 3.0])

    def test_flagged_and_unfinished_rows_are_ignored(self):
        text = gesture(dump(row(0, 90, input_id=1, flags=1, app_ms=60), row(16, -858, input_id=2),
                            row(33, 17, input_id=3, app_ms=5)))
        self.assertEqual(framestats.first_frames(text), [5.0])

    def test_without_marks_there_are_no_gestures(self):
        self.assertEqual(framestats.first_frames(dump(row(0, 40, input_id=1))), [])

    def test_summary_counts_first_frames_of_the_app_within_17_ms(self):
        text = "".join(gesture(dump(row(0, 30, input_id=1, app_ms=a))) for a in (12, 17, 17.4, 40))
        line = framestats.summary(framestats.parse(text), framestats.first_frames(text),
                                  framestats.first_frames(text, "total"))
        self.assertIn("primer fotograma (app) ≤ 17 ms en 2 de 4", line)
        self.assertIn("primeros (app) 12 17 17 40", line)
        self.assertIn("primeros (enteros) 30 30 30 30", line)

    def test_summary_without_gestures_is_as_before(self):
        text = dump(row(0, 15), row(16, 40))
        line = framestats.summary(framestats.parse(text))
        self.assertEqual(line, "fotogramas 2 | lentos 50.0 % | tirones 1 | peor 40 ms | p90 40.0 ms | mediana 27.5 ms")

    def test_unfinished_rows_do_not_count_as_frames(self):
        self.assertEqual(len(framestats.parse(dump(row(0, 15), row(16, -858)))), 1)


class SlowOptionTest(unittest.TestCase):
    """Las opciones de una pasada de `medir.sh transiciones` (011 P1)."""

    def test_slow_percent_counts_frames_finished_after_their_deadline(self):
        frames = framestats.parse(dump(row(0, 15), row(16, 20), row(33, 40), row(50, 50)))
        self.assertEqual(framestats.slow_percent(frames), 50.0)

    def test_first_window_keeps_only_the_frames_that_start_in_it(self):
        text = dump(row(0, 40), row(100, 15), row(300, 40), row(900, 40), row(1200, 15))
        self.assertEqual(len(framestats.parse(text)), 5)
        early = framestats.parse(text, 400)
        self.assertEqual(len(early), 3)
        self.assertEqual(framestats.slow_percent(early), round(100 * 2 / 3, 1))

    def test_slow_percent_of_nothing_is_none(self):
        self.assertIsNone(framestats.slow_percent(framestats.parse(dump(row(0, -5)))))

    def test_jank_count_is_the_frames_over_32_ms(self):
        frames = framestats.parse(dump(row(0, 15), row(16, 33), row(33, 40), row(50, 32)))
        self.assertEqual(framestats.jank_count(frames), 2)

    def test_hz_from_the_gap_between_vsyncs(self):
        at120 = dump(*[row(i * 8.333, 6) for i in range(12)])
        at60 = dump(*[row(i * 16.667, 10) for i in range(12)])
        at90 = dump(*[row(i * 11.111, 8) for i in range(12)])
        self.assertEqual(framestats.refresh_hz(at120), 120)
        self.assertEqual(framestats.refresh_hz(at60), 60)
        self.assertEqual(framestats.refresh_hz(at90), 90)

    def test_hz_ignores_pauses_and_empty_dumps(self):
        text = dump(*[row(i * 8.333, 6) for i in range(8)], row(2000, 6), row(2008.333, 6))
        self.assertEqual(framestats.refresh_hz(text), 120)
        self.assertIsNone(framestats.refresh_hz(dump(row(0, 6))))


def steady(hz, durations, deadlines):
    """Fotogramas de una app que pinta a [hz]: uno por vsync, con la duración y el plazo de Android de cada uno (en ciclo)."""
    step = 1000 / hz
    return dump(*[row(i * step, durations[i % len(durations)], deadline_ms=deadlines[i % len(deadlines)])
                  for i in range(120)])


# El plazo de Android según el estado de frecuencia de ColorOS (014): con `renderFrameRate` en reposo a 60,0, a dos vsync
# de la app (~33 ms); a 120,00001, sobre una línea de 120 Hz (11,6 / 19,9 ms desde el vsync, de 8,33 en 8,33) aunque la
# app pinte a 60.
STATE_60 = (33.3,)
STATE_120 = (11.6, 11.6, 19.9)


class SlowDefinitionTest(unittest.TestCase):
    """«Lento» no depende del estado de frecuencia (014): el mismo ritmo de fotogramas da el mismo %."""

    def slow(self, text, android=False):
        return framestats.slow_percent(framestats.parse(text, android=android))

    def test_quiet_screen_at_60_hz_is_not_slow_in_either_state(self):
        durations = (13, 14.5, 16, 17.5, 15, 18, 12.5, 16.5)  # lo normal en el OPPO: la GPU tarda 10–18 ms
        in60 = steady(60, durations, STATE_60)
        in120 = steady(60, durations, STATE_120)
        self.assertEqual(self.slow(in60), 0.0)
        self.assertEqual(self.slow(in120), 0.0)
        self.assertEqual(self.slow(in60, android=True), 0.0)       # la definición de Android sí depende del estado
        self.assertGreater(self.slow(in120, android=True), 50.0)

    def test_the_same_real_slow_frames_give_the_same_percentage_in_both_states(self):
        durations = (15, 16, 45, 17, 15, 60, 16, 14, 15, 16)  # 2 de cada 10 pasan de 33 ms
        in60 = steady(60, durations, STATE_60)
        in120 = steady(60, durations, STATE_120)
        self.assertEqual(self.slow(in60), 20.0)
        self.assertEqual(self.slow(in120), 20.0)
        self.assertNotEqual(self.slow(in60, android=True), self.slow(in120, android=True))

    def test_app_at_120_hz_is_slow_only_past_two_vsync_of_60_hz(self):
        durations = (9, 12, 15, 16.5, 11, 14, 36, 13)  # a 120 Hz, 2 vsync serían 16,7 ms: la GPU ya tarda eso
        in60 = steady(120, durations, (28.0, 20.0))
        in120 = steady(120, durations, (11.6, 19.9, 28.2))
        self.assertEqual(self.slow(in60), 12.5)
        self.assertEqual(self.slow(in120), 12.5)

    def test_app_at_90_hz_uses_the_same_limit(self):
        text = steady(90, (10, 20, 33, 34), STATE_120)
        self.assertEqual(self.slow(text), 25.0)

    def test_limit_is_two_vsync_of_the_app_with_a_floor_of_60_hz(self):
        self.assertAlmostEqual(framestats.slow_limit_ms(60), 33.33, places=2)
        self.assertAlmostEqual(framestats.slow_limit_ms(90), 33.33, places=2)
        self.assertAlmostEqual(framestats.slow_limit_ms(120), 33.33, places=2)
        self.assertAlmostEqual(framestats.slow_limit_ms(30), 66.67, places=2)
        self.assertAlmostEqual(framestats.slow_limit_ms(None), 33.33, places=2)

    def test_frame_exactly_on_the_limit_is_not_slow(self):
        self.assertEqual(self.slow(dump(row(0, 33), row(17, 33.5))), 50.0)

    def test_first_window_uses_the_cadence_of_the_whole_dump(self):
        text = dump(*[row(i * 16.667, 16, deadline_ms=11.6) for i in range(30)], row(900, 40, deadline_ms=11.6))
        self.assertEqual(framestats.slow_percent(framestats.parse(text, 400)), 0.0)
        self.assertEqual(framestats.slow_percent(framestats.parse(text, 400, android=True)), 100.0)

    def run_cli(self, *args, text):
        script = os.path.join(os.path.dirname(os.path.abspath(__file__)), "framestats.py")
        return subprocess.run([sys.executable, script, *args], input=text, capture_output=True, text=True, encoding="utf-8", errors="replace")

    def test_command_line_slow_and_slow_android(self):
        text = steady(60, (15, 16, 17), STATE_120)
        self.assertEqual(self.run_cli("--slow", text=text).stdout.strip(), "0.0")
        self.assertEqual(self.run_cli("--slow-android", text=text).stdout.strip(), "66.7")
        self.assertEqual(self.run_cli("--slow-android", "--first", "399", text=text).stdout.strip(), "66.7")
        self.assertEqual(self.run_cli("--slow-android", "--jank", text=text).returncode, 1)  # dos opciones: imprime la ayuda


if __name__ == "__main__":
    unittest.main()
