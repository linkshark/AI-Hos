(function () {
  var params = new URLSearchParams(window.location.search);
  var traceId = params.get('swTraceId');
  var serviceName = params.get('swService') || 'aihos';

  if (!traceId) {
    return;
  }

  function textOf(el) {
    return ((el && (el.innerText || el.textContent)) || '').replace(/\s+/g, ' ').trim();
  }

  function isVisible(el) {
    return !!el && !!(el.offsetWidth || el.offsetHeight || el.getClientRects().length);
  }

  function clickByTexts(texts) {
    var nodes = Array.from(document.querySelectorAll('button, a, span, div, li, label'));
    var target = nodes.find(function (node) {
      return isVisible(node) && texts.includes(textOf(node));
    });
    if (!target) {
      return false;
    }
    target.click();
    return true;
  }

  function setNativeValue(el, value) {
    var prototype = el instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
    var descriptor = Object.getOwnPropertyDescriptor(prototype, 'value');
    if (descriptor && descriptor.set) {
      descriptor.set.call(el, value);
    } else {
      el.value = value;
    }
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
  }

  function findFieldContainer(labelTexts) {
    var labels = Array.from(document.querySelectorAll('body *')).filter(function (node) {
      var text = textOf(node);
      return labelTexts.includes(text);
    });
    for (var i = 0; i < labels.length; i += 1) {
      var container = labels[i].parentElement;
      for (var depth = 0; container && depth < 5; depth += 1, container = container.parentElement) {
        if (container.querySelector('input, .el-select, .el-select__wrapper, .el-input__wrapper')) {
          return container;
        }
      }
    }
    return null;
  }

  function chooseService() {
    var container = findFieldContainer(['服务:', '服务', 'Service:', 'Service']);
    if (!container) {
      return false;
    }
    var wrapper =
      container.querySelector('.el-select__wrapper') ||
      container.querySelector('.el-input__wrapper') ||
      container.querySelector('.el-select') ||
      container.querySelector('input');
    if (!wrapper || !isVisible(wrapper)) {
      return false;
    }
    wrapper.click();
    var options = Array.from(document.querySelectorAll('.el-select-dropdown__item, .el-option, li, span, div'));
    var option = options.find(function (node) {
      return isVisible(node) && textOf(node) === serviceName;
    });
    if (!option) {
      return false;
    }
    option.click();
    return true;
  }

  function findTraceInput() {
    var direct = Array.from(document.querySelectorAll('input')).find(function (input) {
      return isVisible(input) && /trace/i.test(input.placeholder || '');
    });
    if (direct) {
      return direct;
    }
    var container = findFieldContainer(['追踪ID:', '追踪ID', 'Trace ID:', 'Trace ID']);
    if (!container) {
      return null;
    }
    return Array.from(container.querySelectorAll('input')).find(isVisible) || null;
  }

  function clickFirstTraceSegment() {
    var nodes = Array.from(document.querySelectorAll('button, li, div, span'));
    var target = nodes.find(function (node) {
      if (!isVisible(node)) {
        return false;
      }
      var text = textOf(node);
      return /\b(GET|POST|PUT|DELETE|PATCH):/.test(text) && /ms/.test(text);
    });
    if (!target) {
      return false;
    }
    target.click();
    return true;
  }

  var traceTabClicked = false;
  var serviceChosen = false;
  var searchClicked = false;
  var segmentOpened = false;
  var attempts = 0;

  var timer = window.setInterval(function () {
    attempts += 1;

    if (!traceTabClicked) {
      traceTabClicked = clickByTexts(['Trace']);
    }

    if (traceTabClicked && !serviceChosen) {
      serviceChosen = chooseService();
    }

    var traceInput = findTraceInput();
    if (traceInput && traceInput.value !== traceId) {
      setNativeValue(traceInput, traceId);
    }

    if (traceInput && traceInput.value === traceId && !searchClicked) {
      searchClicked = clickByTexts(['搜索', 'Search']);
    }

    if (searchClicked && !segmentOpened) {
      segmentOpened = clickFirstTraceSegment();
    }

    if ((searchClicked && segmentOpened) || attempts > 50) {
      window.clearInterval(timer);
    }
  }, 350);
})();
