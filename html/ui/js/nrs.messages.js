/******************************************************************************
 * Copyright © 2020-2021    The BENED Developers.                             *
 *                                                                            *
 * See the AUTHORS.txt, DEVELOPER-AGREEMENT.txt and LICENSE.txt files at      *
 * the top-level directory of this distribution for the individual copyright  *
 * holder information and the developer policies on copyright and licensing.  *
 *                                                                            *
 * Unless otherwise agreed in a custom licensing agreement, no part of the    *
 * BENED software, including this file, may be copied, modified, propagated,    *
 * or distributed except according to the terms contained in the LICENSE.txt  *
 * file.                                                                      *
 *                                                                            *
 * Removal or modification of this copyright notice is prohibited.            *
 *                                                                            *
 ******************************************************************************/

/**
 * @depends {nrs.js}
 */
var NRS = (function(NRS, $) {
    var _messages;
    var _latestMessages;

    NRS.resetMessagesState = function () {
        _messages = {};
        _latestMessages = {};
	};
	NRS.resetMessagesState();

	NRS.pages.messages = function(callback) {
		_messages = {};
        $("#inline_message_form").hide();
        $("#message_details").empty();
        $("#no_message_selected").show();
		$(".content.content-stretch:visible").width($(".page:visible").width());

		NRS.sendRequest("getBlockchainTransactions+", {
			"account": NRS.account,
			"firstIndex": 0,
			"lastIndex": 75,
			"type": 1,
			"subtype": 0
		}, function(response) {
			if (response.transactions && response.transactions.length) {
				for (var i = 0; i < response.transactions.length; i++) {
					var otherUser = (response.transactions[i].recipient == NRS.account ? response.transactions[i].sender : response.transactions[i].recipient);
					if (!(otherUser in _messages)) {
						_messages[otherUser] = [];
					}
					_messages[otherUser].push(response.transactions[i]);
				}
				displayMessageSidebar(callback);
			} else {
				$("#no_message_selected").hide();
				$("#no_messages_available").show();
				$("#messages_sidebar").empty();
				NRS.pageLoaded(callback);
			}
		});
	};

	NRS.setup.messages = function() {
		var options = {
			"id": 'sidebar_messages',
			"titleHTML": '<svg width="21" height="21" viewBox="0 0 21 21" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M10.4999 0.083252C9.67861 0.083252 8.88671 0.187121 8.13073 0.362996C7.97308 0.399792 7.83076 0.484644 7.72343 0.605826C7.6161 0.727008 7.54905 0.878536 7.53157 1.03947L7.36575 2.55111C7.31152 3.04646 7.02538 3.48474 6.59366 3.73417C6.16279 3.98311 5.63981 4.01068 5.18375 3.81047H5.18273L3.79418 3.1991C3.64607 3.13392 3.48138 3.11633 3.32285 3.14877C3.16432 3.1812 3.01978 3.26206 2.90917 3.38017C1.82975 4.53095 1.0013 5.92443 0.530833 7.4797C0.48397 7.63449 0.486298 7.80002 0.537498 7.95343C0.588697 8.10685 0.686244 8.2406 0.816681 8.33622L2.04857 9.23954C2.45084 9.53529 2.68741 10.0014 2.68741 10.4999C2.68741 10.9987 2.45085 11.4652 2.04857 11.7603L0.816681 12.6626C0.686244 12.7582 0.588697 12.892 0.537498 13.0454C0.486298 13.1988 0.48397 13.3643 0.530833 13.5191C1.00125 15.0742 1.82904 16.4687 2.90917 17.6197C3.0199 17.7376 3.1645 17.8183 3.32302 17.8505C3.48155 17.8828 3.64617 17.865 3.79418 17.7997L5.18273 17.1884C5.63902 16.9877 6.1625 17.0166 6.59366 17.2657C7.02538 17.5151 7.31152 17.9534 7.36575 18.4487L7.53157 19.9604C7.54919 20.121 7.61617 20.2722 7.72329 20.3932C7.83041 20.5141 7.9724 20.5989 8.12971 20.6358C8.88604 20.8123 9.67861 20.9166 10.4999 20.9166C11.3212 20.9166 12.1131 20.8127 12.8691 20.6368C13.0267 20.6 13.1691 20.5152 13.2764 20.394C13.3837 20.2728 13.4508 20.1213 13.4683 19.9604L13.6341 18.4487C13.6883 17.9534 13.9744 17.5151 14.4062 17.2657C14.837 17.0167 15.36 16.9881 15.8161 17.1884L17.2056 17.7997C17.3536 17.865 17.5183 17.8828 17.6768 17.8505C17.8353 17.8183 17.9799 17.7376 18.0906 17.6197C19.1701 16.4689 19.9985 15.0744 20.469 13.5191C20.5158 13.3643 20.5135 13.1988 20.4623 13.0454C20.4111 12.892 20.3136 12.7582 20.1831 12.6626L18.9512 11.7603C18.549 11.4652 18.3124 10.9987 18.3124 10.4999C18.3124 10.0011 18.549 9.53464 18.9512 9.23954L20.1831 8.33724C20.3136 8.24161 20.4111 8.10787 20.4623 7.95445C20.5135 7.80103 20.5158 7.63551 20.469 7.48071C19.9985 5.92545 19.1701 4.53095 18.0906 3.38017C17.9799 3.26223 17.8353 3.18157 17.6768 3.14932C17.5183 3.11707 17.3536 3.13481 17.2056 3.20011L15.8161 3.81148C15.36 4.0117 14.837 3.98311 14.4062 3.73417C13.9744 3.48474 13.6883 3.04646 13.6341 2.55111L13.4683 1.03947C13.4506 0.878853 13.3836 0.727649 13.2765 0.606681C13.1694 0.485714 13.0274 0.400933 12.8701 0.364014C12.1138 0.1875 11.3212 0.083252 10.4999 0.083252ZM10.4999 1.64575C11.0074 1.64575 11.4947 1.73684 11.9831 1.82275L12.0807 2.72099C12.189 3.71001 12.7639 4.58968 13.6249 5.08712C14.4865 5.58488 15.5354 5.64211 16.4457 5.24174L17.2718 4.87858C17.906 5.64023 18.4082 6.50054 18.759 7.44308L18.0266 7.98018C17.2247 8.56842 16.7499 9.50498 16.7499 10.4999C16.7499 11.4949 17.2247 12.4314 18.0266 13.0197L18.759 13.5568C18.4082 14.4993 17.906 15.3596 17.2718 16.1213L16.4457 15.7581C15.5354 15.3577 14.4865 15.415 13.6249 15.9127C12.7639 16.4102 12.189 17.2898 12.0807 18.2789L11.9831 19.1771C11.4947 19.2628 11.0071 19.3541 10.4999 19.3541C9.99246 19.3541 9.50516 19.263 9.01675 19.1771L8.9191 18.2789C8.81083 17.2898 8.23589 16.4102 7.37491 15.9127C6.51336 15.415 5.46444 15.3577 4.55407 15.7581L3.72806 16.1213C3.09365 15.3597 2.59155 14.4994 2.24083 13.5568L2.97326 13.0197C3.77515 12.4314 4.24991 11.4949 4.24991 10.4999C4.24991 9.50498 3.77476 8.56782 2.97326 7.97917L2.24083 7.44206C2.59175 6.49915 3.09441 5.63942 3.72908 4.87756L4.55407 5.24072C5.46444 5.64109 6.51336 5.58488 7.37491 5.08712C8.23589 4.58968 8.81083 3.71001 8.9191 2.72099L9.01675 1.82275C9.50512 1.73708 9.99271 1.64575 10.4999 1.64575ZM10.4999 6.33325C8.20797 6.33325 6.33324 8.20799 6.33324 10.4999C6.33324 12.7919 8.20797 14.6666 10.4999 14.6666C12.7918 14.6666 14.6666 12.7919 14.6666 10.4999C14.6666 8.20799 12.7918 6.33325 10.4999 6.33325ZM10.4999 7.89575C11.9474 7.89575 13.1041 9.05242 13.1041 10.4999C13.1041 11.9474 11.9474 13.1041 10.4999 13.1041C9.05241 13.1041 7.89574 11.9474 7.89574 10.4999C7.89574 9.05242 9.05241 7.89575 10.4999 7.89575Z" fill="black"/></svg><span data-i18n="messages">Messages</span>',
			"page": 'messages',
			"desiredPosition": 30,
			"depends": { tags: [ NRS.constants.API_TAGS.MESSAGES ] }
		};
		NRS.addSimpleSidebarMenuItem(options);
	};

	function displayMessageSidebar(callback) {
		var activeAccount = false;
		var messagesSidebar = $("#messages_sidebar");
		var $active = messagesSidebar.find("a.active");
		if ($active.length) {
			activeAccount = $active.data("account");
		}

		var rows = "";
		var sortedMessages = [];
		for (var otherUser in _messages) {
			if (!_messages.hasOwnProperty(otherUser)) {
				continue;
			}
			_messages[otherUser].sort(function (a, b) {
				if (a.timestamp > b.timestamp) {
					return 1;
				} else if (a.timestamp < b.timestamp) {
					return -1;
				} else {
					return 0;
				}
			});

			var otherUserRS = (otherUser == _messages[otherUser][0].sender ? _messages[otherUser][0].senderRS : _messages[otherUser][0].recipientRS);
			sortedMessages.push({
				"timestamp": _messages[otherUser][_messages[otherUser].length - 1].timestamp,
				"user": otherUser,
				"userRS": otherUserRS
			});
		}

		sortedMessages.sort(function (a, b) {
			if (a.timestamp < b.timestamp) {
				return 1;
			} else if (a.timestamp > b.timestamp) {
				return -1;
			} else {
				return 0;
			}
		});

		for (var i = 0; i < sortedMessages.length; i++) {
			var sortedMessage = sortedMessages[i];
			var extra = "";
			if (sortedMessage.user in NRS.contacts) {
				extra = "data-contact='" + NRS.getAccountTitle(sortedMessage, "user") + "' data-context='messages_sidebar_update_context'";
			}
			rows += "<a href='#' class='list-group-item' data-account='" + NRS.getAccountFormatted(sortedMessage, "user") + "' data-account-id='" + NRS.getAccountFormatted(sortedMessage.user) + "' " + extra + ">" +
				"<h4 class='list-group-item-heading'>" + NRS.getAccountTitle(sortedMessage, "user") + "</h4>" +
				"<p class='list-group-item-text'>" + NRS.formatTimestamp(sortedMessage.timestamp) + "</p></a>";
		}
		messagesSidebar.empty().append(rows);
		if (activeAccount) {
			messagesSidebar.find("a[data-account=" + activeAccount + "]").addClass("active").trigger("click");
		}
		NRS.pageLoaded(callback);
	}

	NRS.incoming.messages = function(transactions) {
		if (NRS.hasTransactionUpdates(transactions)) {
			if (transactions.length) {
				for (var i=0; i<transactions.length; i++) {
					var trans = transactions[i];
					if (trans.confirmed && trans.type == 1 && trans.subtype == 0 && trans.senderRS != NRS.accountRS) {
						if (trans.height >= NRS.lastBlockHeight - 3 && !_latestMessages[trans.transaction]) {
							_latestMessages[trans.transaction] = trans;
							$.growl($.t("you_received_message", {
								"account": NRS.getAccountFormatted(trans, "sender"),
								"name": NRS.getAccountTitle(trans, "sender")
							}), {
								"type": "success"
							});
						}
					}
				}
			}
			if (NRS.currentPage == "messages") {
				NRS.loadPage("messages");
			}
		}
	};

	$("#messages_sidebar").on("click", "a", function(e) {
		e.preventDefault();
		$("#messages_sidebar").find("a.active").removeClass("active");
		$(this).addClass("active");
		var otherUser = $(this).data("account-id");
		$("#no_message_selected, #no_messages_available").hide();
		$("#inline_message_recipient").val(otherUser);
		$("#inline_message_form").show();

		var last_day = "";
		var output = "<dl class='chat'>";
		var messages = _messages[otherUser];
		if (messages) {
			for (var i = 0; i < messages.length; i++) {
				var decoded = false;
				var extra = "";
				var type = "";
				if (!messages[i].attachment) {
					decoded = $.t("message_empty");
				} else if (messages[i].attachment.encryptedMessage) {
					try {
						decoded = NRS.tryToDecryptMessage(messages[i]);
						extra = "decrypted";
					} catch (err) {
						if (err.errorCode && err.errorCode == 1) {
							decoded = $.t("error_decryption_passphrase_required");
							extra = "to_decrypt";
						} else {
							decoded = $.t("error_decryption_unknown");
						}
					}
				} else if (messages[i].attachment.message) {
					if (!messages[i].attachment["version.Message"] && !messages[i].attachment["version.PrunablePlainMessage"]) {
						try {
							decoded = converters.hexStringToString(messages[i].attachment.message);
						} catch (err) {
							//legacy
							if (messages[i].attachment.message.indexOf("feff") === 0) {
								decoded = NRS.convertFromHex16(messages[i].attachment.message);
							} else {
								decoded = NRS.convertFromHex8(messages[i].attachment.message);
							}
						}
					} else {
						decoded = String(messages[i].attachment.message);
					}
				} else if (messages[i].attachment.messageHash || messages[i].attachment.encryptedMessageHash) {
					decoded = $.t("message_pruned");
				} else {
					decoded = $.t("message_empty");
				}
				if (decoded !== false) {
					if (!decoded) {
						decoded = $.t("message_empty");
					}
					decoded = String(decoded).escapeHTML().nl2br();
					if (extra == "to_decrypt") {
						decoded = "<i class='fa fa-warning'></i> " + decoded;
					} else if (extra == "decrypted") {
						if (type == "payment") {
							decoded = "<strong>+" + NRS.formatAmount(messages[i].amountNQT) + " BENED</strong><br />" + decoded;
						}
						decoded = "<i class='fa fa-lock'></i> " + decoded;
					}
				} else {
					decoded = "<i class='fa fa-warning'></i> " + $.t("error_could_not_decrypt_message");
					extra = "decryption_failed";
				}
				var day = NRS.formatTimestamp(messages[i].timestamp, true);
				if (day != last_day) {
					output += "<dt><strong>" + day + "</strong></dt>";
					last_day = day;
				}
				output += "<dd class='" + (messages[i].recipient == NRS.account ? "from" : "to") + (extra ? " " + extra : "") + "'><p>" + decoded + "</p></dd>";
			}
		}
		output += "</dl>";
		$("#message_details").empty().append(output);
        var splitter = $('#messages_page').find('.content-splitter-right-inner');
        splitter.scrollTop(splitter[0].scrollHeight);
	});

	$("#messages_sidebar_context").on("click", "a", function(e) {
		e.preventDefault();
		var account = NRS.getAccountFormatted(NRS.selectedContext.data("account"));
		var option = $(this).data("option");
		NRS.closeContextMenu();
		if (option == "add_contact") {
			$("#add_contact_account_id").val(account).trigger("blur");
			$("#add_contact_modal").modal("show");
		} else if (option == "send_cointech") {
			$("#send_money_recipient").val(account).trigger("blur");
			$("#send_money_modal").modal("show");
		} else if (option == "account_info") {
			NRS.showAccountModal(account);
		}
	});

	$("#messages_sidebar_update_context").on("click", "a", function(e) {
		e.preventDefault();
		var account = NRS.getAccountFormatted(NRS.selectedContext.data("account"));
		var option = $(this).data("option");
		NRS.closeContextMenu();
		if (option == "update_contact") {
			$("#update_contact_modal").modal("show");
		} else if (option == "send_cointech") {
			$("#send_money_recipient").val(NRS.selectedContext.data("contact")).trigger("blur");
			$("#send_money_modal").modal("show");
		}
	});

	$("body").on("click", "a[data-goto-messages-account]", function(e) {
		e.preventDefault();
		var account = $(this).data("goto-messages-account");
		NRS.goToPage("messages", function(){ $('#message_sidebar').find('a[data-account=' + account + ']').trigger('click'); });
	});

	NRS.forms.sendMessage = function($modal) {
		var data = NRS.getFormData($modal.find("form:first"));
		var converted = $modal.find("input[name=converted_account_id]").val();
		if (converted) {
			data.recipient = converted;
		}
		return {
			"data": data
		};
	};

	$("#inline_message_form").submit(function(e) {
		e.preventDefault();
        var passpharse = $("#inline_message_password").val();
        var data = {
			"recipient": $.trim($("#inline_message_recipient").val()),
			"feeBENED": "0.150000 or 1%",
			"deadline": "1440",
			"secretPhrase": $.trim(passpharse)
		};

		if (!NRS.rememberPassword) {
			if (passpharse == "") {
				$.growl($.t("error_passphrase_required"), {
					"type": "danger"
				});
				return;
			}
			var accountId = NRS.getAccountId(data.secretPhrase);
			if (accountId != NRS.account) {
				$.growl($.t("error_passphrase_incorrect"), {
					"type": "danger"
				});
				return;
			}
		}

		data.message = $.trim($("#inline_message_text").val());
		var $btn = $("#inline_message_submit");
		$btn.button("loading");
		var requestType = "sendMessage";
		if ($("#inline_message_encrypt").is(":checked")) {
			data.encrypt_message = true;
		}
		if (data.message) {
			try {
				data = NRS.addMessageData(data, "sendMessage");
			} catch (err) {
				$.growl(String(err.message).escapeHTML(), {
					"type": "danger"
				});
				return;
			}
		} else {
			data["_extra"] = {
				"message": data.message
			};
		}

		NRS.sendRequest(requestType, data, function(response) {
			if (response.errorCode) {
				$.growl(NRS.translateServerError(response).escapeHTML(), {
					type: "danger"
				});
			} else if (response.fullHash) {
				$.growl($.t("success_message_sent"), {
					type: "success"
				});
				$("#inline_message_text").val("");
				if (data["_extra"].message && data.encryptedMessageData) {
					NRS.addDecryptedTransaction(response.transaction, {
						"encryptedMessage": String(data["_extra"].message)
					});
				}

                NRS.addUnconfirmedTransaction(response.transaction, function (alreadyProcessed) {
                    if (!alreadyProcessed) {
                        $("#message_details").find("dl.chat").append("<dd class='to tentative" + (data.encryptedMessageData ? " decrypted" : "") + "'><p>" + (data.encryptedMessageData ? "<i class='fa fa-lock'></i> " : "") + (!data["_extra"].message ? $.t("message_empty") : String(data["_extra"].message).escapeHTML()) + "</p></dd>");
                        var splitter = $('#messages_page').find('.content-splitter-right-inner');
                        splitter.scrollTop(splitter[0].scrollHeight);
                    }
                });
				//leave password alone until user moves to another page.
			} else {
				//TODO
				$.growl($.t("error_send_message"), {
					type: "danger"
				});
			}
			$btn.button("reset");
		});
	});

	NRS.forms.sendMessageComplete = function(response, data) {
		data.message = data._extra.message;
		if (!(data["_extra"] && data["_extra"].convertedAccount)) {
			$.growl($.t("success_message_sent") + " <a href='#' data-account='" + NRS.getAccountFormatted(data, "recipient") + "' data-toggle='modal' data-target='#add_contact_modal' style='text-decoration:underline'>" + $.t("add_recipient_to_contacts_q") + "</a>", {
				"type": "success"
			});
		} else {
			$.growl($.t("success_message_sent"), {
				"type": "success"
			});
		}
	};

	$("#message_details").on("click", "dd.to_decrypt", function() {
		$("#messages_decrypt_modal").modal("show");
	});

	NRS.forms.decryptMessages = function($modal) {
		var data = NRS.getFormData($modal.find("form:first"));
		var success = false;
		try {
			var messagesToDecrypt = [];
			for (var otherUser in _messages) {
				if (!_messages.hasOwnProperty(otherUser)) {
					continue;
				}
				for (var key in _messages[otherUser]) {
					if (!_messages[otherUser].hasOwnProperty(key)) {
						continue;
					}
					var message = _messages[otherUser][key];
					if (message.attachment && message.attachment.encryptedMessage) {
						messagesToDecrypt.push(message);
					}
				}
			}
			success = NRS.decryptAllMessages(messagesToDecrypt, data.secretPhrase);
		} catch (err) {
			if (err.errorCode && err.errorCode <= 2) {
				return {
					"error": err.message.escapeHTML()
				};
			} else {
				return {
					"error": $.t("error_messages_decrypt")
				};
			}
		}

		if (data.rememberPassword) {
			NRS.setDecryptionPassword(data.secretPhrase);
		}
		$("#messages_sidebar").find("a.active").trigger("click");
		if (success) {
			$.growl($.t("success_messages_decrypt"), {
				"type": "success"
			});
		} else {
			$.growl($.t("error_messages_decrypt"), {
				"type": "danger"
			});
		}
		return {
			"stop": true
		};
	};

	return NRS;
}(NRS || {}, jQuery));
