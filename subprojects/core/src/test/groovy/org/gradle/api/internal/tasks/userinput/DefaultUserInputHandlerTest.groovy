/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.tasks.userinput


import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.events.PromptOutputEvent
import org.gradle.internal.logging.events.UserInputRequestEvent
import org.gradle.internal.logging.events.UserInputResumeEvent
import org.gradle.internal.time.Clock
import org.gradle.util.TestUtil
import org.gradle.util.internal.TextUtil
import spock.lang.Specification
import spock.lang.Subject

class DefaultUserInputHandlerTest extends Specification {

    private static final String TEXT = 'Accept license?'
    def outputEventBroadcaster = Mock(OutputEventListener)
    def userInputReader = Mock(UserInputReader)
    def clock = Mock(Clock)
    @Subject
    def userInputHandler = new DefaultUserInputHandler(outputEventBroadcaster, clock, userInputReader, TestUtil.providerFactory())

    def "ask required yes/no question"() {
        when:
        def input = ask { it.askYesNoQuestion(TEXT) }

        then:
        1 * outputEventBroadcaster.onOutput(_ as UserInputRequestEvent)
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt == TextUtil.platformLineSeparator }
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt.trim() == 'Accept license? [yes, no]' }
        1 * userInputReader.readInput() >> enteredUserInput
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt == TextUtil.platformLineSeparator }
        1 * outputEventBroadcaster.onOutput(_ as UserInputResumeEvent)
        0 * outputEventBroadcaster._
        0 * userInputHandler._

        and:
        input == sanitizedUserInput

        where:
        enteredUserInput | sanitizedUserInput
        null             | null
        'yes   '         | true
        'yes'            | true
        '   no   '       | false
        'y\u0000es '     | true
    }

    def "required yes/no question returns null on end-of-input"() {
        when:
        def input = ask { it.askYesNoQuestion(TEXT) }

        then:
        1 * userInputReader.readInput() >> null
        0 * userInputHandler._

        and:
        input == null
    }

    def "prompts user again on invalid response to required yes/no question"() {
        when:
        def input = ask { it.askYesNoQuestion(TEXT) }

        then:
        1 * outputEventBroadcaster.onOutput(_ as UserInputRequestEvent)
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt == TextUtil.platformLineSeparator }
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt.trim() == 'Accept license? [yes, no]' }
        1 * userInputReader.readInput() >> invalidInput
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt.trim() == "Please enter 'yes' or 'no':" }
        1 * userInputReader.readInput() >> 'no'
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt == TextUtil.platformLineSeparator }
        1 * outputEventBroadcaster.onOutput(_ as UserInputResumeEvent)
        0 * outputEventBroadcaster._
        0 * userInputHandler._

        and:
        input == false

        where:
        invalidInput | _
        ''           | _
        'bla'        | _
        'y'          | _
        'Y'          | _
        'ye'         | _
        'YES'        | _
        'n'          | _
        'NO'         | _
    }

    def "can ask yes/no question"() {
        when:
        def input = ask { it.askBooleanQuestion(TEXT, true) }

        then:
        1 * outputEventBroadcaster.onOutput(_ as UserInputRequestEvent)
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt == TextUtil.platformLineSeparator }
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt.trim() == 'Accept license? (default: yes) [yes, no]' }
        1 * userInputReader.readInput() >> enteredUserInput
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt == TextUtil.platformLineSeparator }
        1 * outputEventBroadcaster.onOutput(_ as UserInputResumeEvent)
        0 * outputEventBroadcaster._
        0 * userInputHandler._

        and:
        input == expected

        where:
        enteredUserInput | expected
        null             | true
        'yes'            | true
        'y'              | true
        'Y'              | true
        'YES'            | true
        'yes   '         | true
        ' y   '          | true
        'no'             | false
        'n'              | false
        'N'              | false
        '   no   '       | false
        '   n   '        | false
    }

    def "yes/no question returns default when empty input line received"() {
        when:
        def input = ask { it.askBooleanQuestion(TEXT, true) }

        then:
        1 * outputEventBroadcaster.onOutput(_ as UserInputRequestEvent)
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt == TextUtil.platformLineSeparator }
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt.trim() == 'Accept license? (default: yes) [yes, no]' }
        1 * userInputReader.readInput() >> ""
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt == TextUtil.platformLineSeparator }
        1 * outputEventBroadcaster.onOutput(_ as UserInputResumeEvent)
        0 * outputEventBroadcaster._
        0 * userInputHandler._

        and:
        input == true
    }

    def "prompts user again on invalid response to yes/no question"() {
        when:
        def input = ask { it.askBooleanQuestion(TEXT, true) }

        then:
        1 * outputEventBroadcaster.onOutput(_ as UserInputRequestEvent)
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt == TextUtil.platformLineSeparator }
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt.trim() == 'Accept license? (default: yes) [yes, no]' }
        1 * userInputReader.readInput() >> invalidInput
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt.trim() == "Please enter 'yes' or 'no' (default: 'yes'):" }
        1 * userInputReader.readInput() >> 'no'
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt == TextUtil.platformLineSeparator }
        1 * outputEventBroadcaster.onOutput(_ as UserInputResumeEvent)
        0 * outputEventBroadcaster._
        0 * userInputHandler._

        and:
        input == false

        where:
        invalidInput | _
        'bla'        | ''
        'nope'       | ''
        'yep'        | ''
    }

    def "can ask select question"() {
        when:
        def input = ask { it.selectOption("select option", [11, 12, 13], 12) }

        then:
        1 * outputEventBroadcaster.onOutput(_ as UserInputRequestEvent)
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt == TextUtil.platformLineSeparator }
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event ->
            assert event.prompt == TextUtil.toPlatformLineSeparators("""select option:
  1: 11
  2: 12
  3: 13
Enter selection (default: 12) [1..3] """)
        }
        1 * userInputReader.readInput() >> " 3  "
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt == TextUtil.platformLineSeparator }
        1 * outputEventBroadcaster.onOutput(_ as UserInputResumeEvent)
        0 * outputEventBroadcaster._
        0 * userInputHandler._

        and:
        input == 13
    }

    def "can define how to render select options"() {
        when:
        def input = ask {
            it.choice("select option", [11, 12, 13])
                .renderUsing { it + "!" }
                .ask()
        }

        then:
        1 * outputEventBroadcaster.onOutput(_ as UserInputRequestEvent)
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt == TextUtil.platformLineSeparator }
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event ->
            assert event.prompt == TextUtil.toPlatformLineSeparators("""select option:
  1: 11!
  2: 12!
  3: 13!
Enter selection (default: 11!) [1..3] """)
        }
        1 * userInputReader.readInput() >> " 3  "
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt == TextUtil.platformLineSeparator }
        1 * outputEventBroadcaster.onOutput(_ as UserInputResumeEvent)
        0 * outputEventBroadcaster._
        0 * userInputHandler._

        and:
        input == 13
    }

    def "select question does not prompt user when there is only one option"() {
        when:
        def input = ask { it.selectOption(TEXT, [11], 11) }

        then:
        0 * outputEventBroadcaster.onOutput(_)
        0 * userInputHandler._

        and:
        input == 11
    }

    def "select question returns default when empty input line received"() {
        when:
        def input = ask { it.selectOption(TEXT, [11, 12, 13], 12) }

        then:
        1 * userInputReader.readInput() >> ""
        0 * userInputHandler._

        and:
        input == 12
    }

    def "prompts user again on invalid response to select question"() {
        when:
        def input = ask { it.selectOption(TEXT, [11, 12, 13], 12) }

        then:
        1 * outputEventBroadcaster.onOutput(_ as UserInputRequestEvent)
        2 * outputEventBroadcaster.onOutput(_ as PromptOutputEvent)
        1 * userInputReader.readInput() >> 'bla'
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt.trim() == "Please enter a value between 1 and 3:" }
        1 * userInputReader.readInput() >> '4'
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt.trim() == "Please enter a value between 1 and 3:" }
        1 * userInputReader.readInput() >> '0'
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt.trim() == "Please enter a value between 1 and 3:" }
        1 * userInputReader.readInput() >> '-2'
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt.trim() == "Please enter a value between 1 and 3:" }
        1 * userInputReader.readInput() >> '1'
        1 * outputEventBroadcaster.onOutput(_ as PromptOutputEvent)
        1 * outputEventBroadcaster.onOutput(_ as UserInputResumeEvent)
        0 * outputEventBroadcaster._
        0 * userInputHandler._

        and:
        input == 11
    }

    def "select question returns default on end-of-input"() {
        when:
        def input = ask { it.selectOption(TEXT, [11, 12, 13], 12) }

        then:
        1 * userInputReader.readInput() >> null
        0 * userInputHandler._

        and:
        input == 12
    }

    def "choice returns first option on end-of-input when no default specified"() {
        when:
        def choice = ask { it.choice(TEXT, [11, 12, 13]) }

        then:
        choiceUsesDefault(choice, 11)
    }

    def "choice returns default option on end-of-input"() {
        when:
        def choice = ask { it.choice(TEXT, [11, 12, 13]).defaultOption(12) }

        then:
        choiceUsesDefault(choice, 12)
    }

    def "choice ignores non-interactive default value"() {
        when:
        def choice = ask { it.choice(TEXT, [11, 12, 13]).whenNotConnected(12) }

        then:
        choiceUsesDefault(choice, 11)
    }

    <T> void choiceUsesDefault(ChoiceBuilder<T> choice, T expected) {
        1 * userInputReader.readInput() >> null
        0 * userInputHandler._

        def input = choice.ask()
        assert input == expected
    }

    def "can ask int question"() {
        when:
        def input = ask { it.askIntQuestion("enter value", 1, 2) }

        then:
        1 * outputEventBroadcaster.onOutput(_ as UserInputRequestEvent)
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt == TextUtil.platformLineSeparator }
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt.trim() == "enter value (min: 1, default: 2):" }
        1 * userInputReader.readInput() >> "12"
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt == TextUtil.platformLineSeparator }
        1 * outputEventBroadcaster.onOutput(_ as UserInputResumeEvent)
        0 * outputEventBroadcaster._
        0 * userInputHandler._

        and:
        input == 12
    }

    def "prompts user again on invalid response to int question"() {
        when:
        def input = ask { it.askIntQuestion("enter value", 1, 2) }

        then:
        1 * outputEventBroadcaster.onOutput(_ as UserInputRequestEvent)
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt == TextUtil.platformLineSeparator }
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt.trim() == "enter value (min: 1, default: 2):" }
        1 * userInputReader.readInput() >> "not an int"
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt.trim() == "Please enter an integer value (min: 1, default: 2):" }
        1 * userInputReader.readInput() >> "12"
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt == TextUtil.platformLineSeparator }
        1 * outputEventBroadcaster.onOutput(_ as UserInputResumeEvent)
        0 * outputEventBroadcaster._
        0 * userInputHandler._

        and:
        input == 12
    }

    def "prompts user again on int question response below minimum value"() {
        when:
        def input = ask { it.askIntQuestion("enter value", 10, 12) }

        then:
        1 * outputEventBroadcaster.onOutput(_ as UserInputRequestEvent)
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt == TextUtil.platformLineSeparator }
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt.trim() == "enter value (min: 10, default: 12):" }
        1 * userInputReader.readInput() >> "9"
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt.trim() == "Please enter an integer value >= 10 (default: 12):" }
        1 * userInputReader.readInput() >> "10"
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt == TextUtil.platformLineSeparator }
        1 * outputEventBroadcaster.onOutput(_ as UserInputResumeEvent)
        0 * outputEventBroadcaster._
        0 * userInputHandler._

        and:
        input == 10
    }

    def "uses default value for int question when empty line input received"() {
        when:
        def input = ask { it.askIntQuestion("enter value", 1, 2) }

        then:
        1 * outputEventBroadcaster.onOutput(_ as UserInputRequestEvent)
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt == TextUtil.platformLineSeparator }
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt.trim() == "enter value (min: 1, default: 2):" }
        1 * userInputReader.readInput() >> ""
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt == TextUtil.platformLineSeparator }
        1 * outputEventBroadcaster.onOutput(_ as UserInputResumeEvent)
        0 * outputEventBroadcaster._
        0 * userInputHandler._

        and:
        input == 2
    }

    def "uses default value for int question when end-of-input received"() {
        when:
        def input = ask { it.askIntQuestion("enter value", 1, 2) }

        then:
        1 * outputEventBroadcaster.onOutput(_ as UserInputRequestEvent)
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt == TextUtil.platformLineSeparator }
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt.trim() == "enter value (min: 1, default: 2):" }
        1 * userInputReader.readInput() >> null
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt == TextUtil.platformLineSeparator }
        1 * outputEventBroadcaster.onOutput(_ as UserInputResumeEvent)
        0 * outputEventBroadcaster._
        0 * userInputHandler._

        and:
        input == 2
    }

    def "can ask text question"() {
        when:
        def input = ask { it.askQuestion("enter value", "value") }

        then:
        1 * outputEventBroadcaster.onOutput(_ as UserInputRequestEvent)
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt == TextUtil.platformLineSeparator }
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt.trim() == "enter value (default: value):" }
        1 * userInputReader.readInput() >> "thing"
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt == TextUtil.platformLineSeparator }
        1 * outputEventBroadcaster.onOutput(_ as UserInputResumeEvent)
        0 * outputEventBroadcaster._
        0 * userInputHandler._

        and:
        input == "thing"
    }

    def "text question returns default when empty input line received"() {
        when:
        def input = ask { it.askQuestion(TEXT, "default") }

        then:
        1 * userInputReader.readInput() >> ""
        0 * userInputHandler._

        and:
        input == "default"
    }

    def "text question returns default on end of input"() {
        when:
        def input = ask { it.askQuestion(TEXT, "default") }

        then:
        1 * userInputReader.readInput() >> null
        0 * userInputHandler._

        and:
        input == "default"
    }

    def "can ask multiple questions in one interaction"() {
        when:
        def input = ask {
            def a = it.askQuestion("enter value", "value")
            def b = it.askQuestion("enter value", "value")
            [a, b]
        }

        then:
        1 * outputEventBroadcaster.onOutput(_ as UserInputRequestEvent)
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt == TextUtil.platformLineSeparator }
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt.trim() == "enter value (default: value):" }
        1 * userInputReader.readInput() >> "thing"
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt == TextUtil.platformLineSeparator }
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt.trim() == "enter value (default: value):" }
        1 * userInputReader.readInput() >> ""
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt == TextUtil.platformLineSeparator }
        1 * outputEventBroadcaster.onOutput(_ as UserInputResumeEvent)
        0 * outputEventBroadcaster._
        0 * userInputHandler._

        and:
        input == ["thing", "value"]
    }

    def "does not update UI when no question is asked during interaction"() {
        when:
        def input = ask { 12 }

        then:
        input == 12

        and:
        0 * outputEventBroadcaster._
        0 * userInputHandler._
    }

    def "user is prompted lazily when provider value is queried"() {
        when:
        def input = userInputHandler.askUser { it.askQuestion("thing?", "value") }

        then:
        0 * outputEventBroadcaster._
        0 * userInputHandler._

        when:
        def result = input.get()

        then:
        1 * outputEventBroadcaster.onOutput(_ as UserInputRequestEvent)
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt == TextUtil.platformLineSeparator }
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt.trim() == "thing? (default: value):" }
        1 * userInputReader.readInput() >> ""
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt == TextUtil.platformLineSeparator }
        1 * outputEventBroadcaster.onOutput(_ as UserInputResumeEvent)
        0 * outputEventBroadcaster._
        0 * userInputHandler._

        and:
        result == "value"
    }

    def "can ask multiple questions in several interaction"() {
        when:
        def input1 = ask { it.askQuestion("enter value", "value") }

        then:
        1 * outputEventBroadcaster.onOutput(_ as UserInputRequestEvent)
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt == TextUtil.platformLineSeparator }
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt.trim() == "enter value (default: value):" }
        1 * userInputReader.readInput() >> "thing"
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt == TextUtil.platformLineSeparator }
        1 * outputEventBroadcaster.onOutput(_ as UserInputResumeEvent)
        0 * outputEventBroadcaster._
        0 * userInputHandler._

        and:
        input1 == "thing"

        when:
        def input2 = ask { it.askQuestion("enter value", "value") }

        then:
        1 * outputEventBroadcaster.onOutput(_ as UserInputRequestEvent)
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt.trim() == "enter value (default: value):" }
        1 * userInputReader.readInput() >> ""
        1 * outputEventBroadcaster.onOutput(_) >> { PromptOutputEvent event -> assert event.prompt == TextUtil.platformLineSeparator }
        1 * outputEventBroadcaster.onOutput(_ as UserInputResumeEvent)
        0 * outputEventBroadcaster._
        0 * userInputHandler._

        and:
        input2 == "value"
    }

    <T> T ask(Closure<T> action) {
        return userInputHandler.askUser(action).getOrNull()
    }

}
