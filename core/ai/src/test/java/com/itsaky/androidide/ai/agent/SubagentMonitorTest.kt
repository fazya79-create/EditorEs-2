/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.ai.agent

import com.google.common.truth.Truth.assertThat
import com.itsaky.androidide.ai.tools.ToolScope
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Test

class SubagentMonitorTest {

  private fun monitor() = SubagentMonitor()

  @Test
  fun `each sub-agent keeps its own feed under its own delegation id`() {
    val monitor = monitor()
    monitor.start(1, "first", "do the first thing", ToolScope.READ_ONLY)
    monitor.start(2, "second", "do the second thing", ToolScope.FULL)

    monitor.record(1, SubagentActivityKind.TOOL_STARTED, title = "read_file", text = "Read a.kt")
    monitor.record(2, SubagentActivityKind.TOOL_STARTED, title = "grep", text = "Search foo")

    val sessions = monitor.snapshot().associateBy { it.id }

    assertThat(sessions.getValue(1).description).isEqualTo("first")
    assertThat(sessions.getValue(1).scope).isEqualTo(ToolScope.READ_ONLY)
    assertThat(sessions.getValue(1).activity.map { it.title }).containsExactly("read_file")

    assertThat(sessions.getValue(2).scope).isEqualTo(ToolScope.FULL)
    assertThat(sessions.getValue(2).activity.map { it.title }).containsExactly("grep")
  }

  @Test
  fun `consecutive deltas of one kind coalesce into a single entry`() {
    val monitor = monitor()
    monitor.start(1, "probe", "look around", ToolScope.READ_ONLY)

    monitor.appendStream(1, SubagentActivityKind.MESSAGE, "Hello")
    monitor.appendStream(1, SubagentActivityKind.MESSAGE, " world")
    monitor.record(1, SubagentActivityKind.TOOL_STARTED, title = "read_file")
    monitor.appendStream(1, SubagentActivityKind.MESSAGE, "again")

    val activity = monitor.snapshot().single().activity

    assertThat(activity).hasSize(3)
    assertThat(activity[0].text).isEqualTo("Hello world")
    assertThat(activity[2].text).isEqualTo("again")
  }

  @Test
  fun `a snapshot is unaffected by later writes`() {
    val monitor = monitor()
    monitor.start(1, "probe", "look around", ToolScope.READ_ONLY)

    val before = monitor.snapshot()
    monitor.record(1, SubagentActivityKind.NOTICE, text = "later")

    assertThat(before.single().activity).isEmpty()
    assertThat(monitor.snapshot().single().activity).hasSize(1)
  }

  @Test
  fun `finishing running sessions leaves already terminal ones alone`() {
    val monitor = monitor()
    monitor.start(1, "done", "a", ToolScope.READ_ONLY)
    monitor.start(2, "still going", "b", ToolScope.READ_ONLY)
    monitor.update(1) { it.copy(status = SubagentStatus.SUCCEEDED, summary = "its own report") }

    monitor.finishRunning(SubagentStatus.FAILED, "cancelled")

    val sessions = monitor.snapshot().associateBy { it.id }
    assertThat(sessions.getValue(1).status).isEqualTo(SubagentStatus.SUCCEEDED)
    assertThat(sessions.getValue(1).summary).isEqualTo("its own report")
    assertThat(sessions.getValue(2).status).isEqualTo(SubagentStatus.FAILED)
    assertThat(sessions.getValue(2).summary).isEqualTo("cancelled")
  }

  @Test
  fun `concurrent writers never lose an activity entry`() {
    val monitor = monitor()
    val writers = 6
    val perWriter = 40

    repeat(writers) { index -> monitor.start(index.toLong(), "w$index", "p", ToolScope.READ_ONLY) }

    val pool = Executors.newFixedThreadPool(writers)
    val start = CountDownLatch(1)
    val done = CountDownLatch(writers)

    repeat(writers) { index ->
      pool.execute {
        start.await()
        repeat(perWriter) { n ->
          monitor.record(index.toLong(), SubagentActivityKind.NOTICE, text = "$index-$n")
        }
        done.countDown()
      }
    }

    start.countDown()
    assertThat(done.await(30, TimeUnit.SECONDS)).isTrue()
    pool.shutdown()

    val sessions = monitor.snapshot()
    assertThat(sessions).hasSize(writers)
    sessions.forEach { assertThat(it.activity).hasSize(perWriter) }

    val ids = sessions.flatMap { session -> session.activity.map { it.id } }
    assertThat(ids.toSet()).hasSize(writers * perWriter)
  }

  @Test
  fun `the change callback fires for every mutation that changed something`() {
    val calls = AtomicInteger(0)
    val monitor = SubagentMonitor { calls.incrementAndGet() }

    monitor.start(1, "probe", "p", ToolScope.READ_ONLY)
    monitor.record(1, SubagentActivityKind.NOTICE, text = "x")
    monitor.setAwaitingApproval(true)
    monitor.setAwaitingApproval(true)

    assertThat(calls.get()).isEqualTo(3)
  }
}
