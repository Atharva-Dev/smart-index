import json
import os
import shutil
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import nfr003_report as r  # noqa: E402
FIXTURE = os.path.join(HERE, "testdata", "sample-benchmarkData.json")
FRAMES_FIXTURE = os.path.join(HERE, "testdata", "benchmarkData_frames.json")


def write_bench(directory, name, key, runs, group="metrics"):
    data = {"context": {"build": {"model": "M"}}, "benchmarks": [{"name": name, group: {key: {"runs": runs}}}]}
    with open(os.path.join(directory, "x-benchmarkData.json"), "w") as fh:
        json.dump(data, fh)


class ReportTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp()
        self.out = os.path.join(self.dir, "out.md")

    def tearDown(self):
        shutil.rmtree(self.dir)

    def run_main(self, d=None):
        return r.main([d or self.dir, "--out", self.out])

    def test_p95_nearest_rank(self):
        self.assertEqual(r.percentile_nearest_rank(list(range(1, 21)), 0.95), 19)
        self.assertEqual(r.percentile_nearest_rank([7], 0.95), 7)

    def test_fixture_rows_for_both_shapes(self):
        d = os.path.join(self.dir, "fx")
        os.makedirs(d)
        shutil.copy(FIXTURE, os.path.join(d, "a-benchmarkData.json"))
        self.assertEqual(self.run_main(d), 0)
        text = open(self.out).read()
        self.assertIn("| search[ra_ALL_null] | SIDX:search:firstPageMs | 20 |", text)
        self.assertIn("| scrollBusyDay | frameDurationCpuMs | 3 |", text)
        self.assertIn("| scrollBusyDay | SIDX:timeline:firstPageMs | 1 |", text)
        self.assertIn("SM-TEST", text)

    def test_sampled_metric_runs_as_list_of_lists_are_flattened(self):
        d = os.path.join(self.dir, "fx")
        os.makedirs(d)
        shutil.copy(FRAMES_FIXTURE, os.path.join(d, "a-benchmarkData.json"))
        self.assertEqual(self.run_main(d), 0)
        text = open(self.out).read()
        self.assertIn("| scrollBusyDay | frameDurationCpuMs | 3 | 5.0 | 6.0 | 6.0 | — |", text)
        self.assertIn("| scrollBusyDay | SIDX:timeline:firstPageMs | 1 |", text)

    def test_empty_flattened_runs_are_skipped(self):
        self.assertEqual(r.collect_metrics({"sampledMetrics": {"frameOverrunMs": {"runs": [[], []]}}}), {})

    def test_flat_runs_unchanged(self):
        self.assertEqual(r.collect_metrics({"metrics": {"SIDX:store:pageMs": {"runs": [1, 2.5]}}}),
                         {"SIDX:store:pageMs": [1.0, 2.5]})

    def test_exit_1_when_gated_p95_at_gate(self):
        write_bench(self.dir, "search[x]", "SIDX:search:firstPageMs", [1000])
        self.assertEqual(self.run_main(), 1)
        self.assertIn("FAIL", open(self.out).read())

    def test_exit_2_when_no_json(self):
        self.assertEqual(self.run_main(), 2)

    def test_missing_gated_metric_is_fail(self):
        write_bench(self.dir, "search[x]", "SIDX:store:pageMs", [10])
        self.assertEqual(self.run_main(), 1)
        self.assertIn("FAIL", open(self.out).read())


if __name__ == "__main__":
    unittest.main()
